Yeh URL directly kholo — seedha edit screen pe le jayega:

**https://github.com/udaysukjora-lgtm/-empty-no-README-/edit/main/main.py**

Wahan textbox ke andar tap karke sab select karo (long-press → "Select all"), delete karo, phir neeche wala poora code paste kar do (code ke corner mein copy icon hai):

```python
import os
import enum
from datetime import datetime, timedelta
from typing import Optional, List, Dict

from fastapi import (
    FastAPI, Depends, HTTPException, WebSocket, WebSocketDisconnect,
    UploadFile, File,
)
from fastapi.middleware.cors import CORSMiddleware
from fastapi.security import OAuth2PasswordBearer
from sqlalchemy import (
    create_engine, Column, BigInteger, String, Text, Boolean, TIMESTAMP,
    Enum, ForeignKey, func,
)
from sqlalchemy.orm import sessionmaker, declarative_base, relationship, Session
from pydantic import BaseModel
from jose import jwt, JWTError
from dotenv import load_dotenv

load_dotenv()

# ---------- Config ----------
DATABASE_URL = os.getenv(
    "DATABASE_URL", "mysql+pymysql://root:password@localhost:3306/messaging_app"
)
JWT_SECRET = os.getenv("JWT_SECRET", "dev-secret-change-me")
JWT_ALGORITHM = os.getenv("JWT_ALGORITHM", "HS256")
JWT_EXPIRE_MINUTES = int(os.getenv("JWT_EXPIRE_MINUTES", "10080"))  # 7 days
DEV_OTP = "123456"  # TODO: replace with real Firebase Phone Auth

# ---------- Database ----------
engine = create_engine(DATABASE_URL, pool_pre_ping=True)
SessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=engine)
Base = declarative_base()


def get_db():
    db = SessionLocal()
    try:
        yield db
    finally:
        db.close()


# ---------- Models ----------
class ConversationType(str, enum.Enum):
    direct = "direct"
    group = "group"


class MessageType(str, enum.Enum):
    text = "text"
    image = "image"
    video = "video"
    audio = "audio"
    file = "file"


class MessageStatus(str, enum.Enum):
    sent = "sent"
    delivered = "delivered"
    read = "read"


class User(Base):
    __tablename__ = "users"

    id = Column(BigInteger, primary_key=True, autoincrement=True)
    phone_number = Column(String(20), unique=True, nullable=False, index=True)
    name = Column(String(100))
    avatar_url = Column(String(500))
    about = Column(String(255), default="Hey there! I am using this app")
    last_seen = Column(TIMESTAMP, nullable=True)
    is_online = Column(Boolean, default=False)
    created_at = Column(TIMESTAMP, server_default=func.now())


class Conversation(Base):
    __tablename__ = "conversations"

    id = Column(BigInteger, primary_key=True, autoincrement=True)
    type = Column(Enum(ConversationType), default=ConversationType.direct)
    created_at = Column(TIMESTAMP, server_default=func.now())

    participants = relationship("ConversationParticipant", back_populates="conversation")
    messages = relationship("Message", back_populates="conversation")


class ConversationParticipant(Base):
    __tablename__ = "conversation_participants"

    conversation_id = Column(BigInteger, ForeignKey("conversations.id"), primary_key=True)
    user_id = Column(BigInteger, ForeignKey("users.id"), primary_key=True)
    joined_at = Column(TIMESTAMP, server_default=func.now())

    conversation = relationship("Conversation", back_populates="participants")
    user = relationship("User")


class Message(Base):
    __tablename__ = "messages"

    id = Column(BigInteger, primary_key=True, autoincrement=True)
    conversation_id = Column(BigInteger, ForeignKey("conversations.id"), index=True)
    sender_id = Column(BigInteger, ForeignKey("users.id"))
    content = Column(Text)
    message_type = Column(Enum(MessageType), default=MessageType.text)
    media_url = Column(String(500))
    status = Column(Enum(MessageStatus), default=MessageStatus.sent)
    created_at = Column(TIMESTAMP, server_default=func.now(), index=True)

    conversation = relationship("Conversation", back_populates="messages")
    sender = relationship("User")


# ---------- Schemas ----------
class SendOTPRequest(BaseModel):
    phone_number: str


class VerifyOTPRequest(BaseModel):
    phone_number: str
    otp: str


class TokenResponse(BaseModel):
    access_token: str
    token_type: str = "bearer"


class MessageOut(BaseModel):
    id: int
    conversation_id: int
    sender_id: int
    content: Optional[str] = None
    message_type: str
    media_url: Optional[str] = None
    status: str
    created_at: datetime

    class Config:
        from_attributes = True


class ConversationCreate(BaseModel):
    participant_phone: str


# ---------- Auth helpers ----------
oauth2_scheme = OAuth2PasswordBearer(tokenUrl="/auth/verify-otp")


def create_access_token(user_id: int) -> str:
    expire = datetime.utcnow() + timedelta(minutes=JWT_EXPIRE_MINUTES)
    payload = {"sub": str(user_id), "exp": expire}
    return jwt.encode(payload, JWT_SECRET, algorithm=JWT_ALGORITHM)


def get_current_user_id(token: str = Depends(oauth2_scheme)) -> int:
    try:
        payload = jwt.decode(token, JWT_SECRET, algorithms=[JWT_ALGORITHM])
        user_id = payload.get("sub")
        if user_id is None:
            raise HTTPException(status_code=401, detail="Invalid token")
        return int(user_id)
    except JWTError:
        raise HTTPException(status_code=401, detail="Invalid or expired token")


# ---------- WebSocket connection manager ----------
class ConnectionManager:
    def __init__(self):
        self.active_connections: Dict[int, WebSocket] = {}

    async def connect(self, user_id: int, websocket: WebSocket):
        await websocket.accept()
        self.active_connections[user_id] = websocket

    def disconnect(self, user_id: int):
        self.active_connections.pop(user_id, None)

    async def send_to_user(self, user_id: int, message: dict):
        ws = self.active_connections.get(user_id)
        if ws:
            await ws.send_json(message)


manager = ConnectionManager()

# ---------- App ----------
app = FastAPI(title="Messaging App API")

# TODO: restrict allow_origins to your actual web/mobile app domain(s) once you have one
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=False,
    allow_methods=["*"],
    allow_headers=["*"],
)

Base.metadata.create_all(bind=engine)


@app.get("/health")
def health_check():
    return {"status": "ok"}


# ---- Auth routes ----
@app.post("/auth/send-otp")
def send_otp(payload: SendOTPRequest):
    # TODO: call Firebase here to actually send the SMS
    return {
        "message": f"OTP sent to {payload.phone_number}",
        "dev_note": f"Use {DEV_OTP} in local dev",
    }


@app.post("/auth/verify-otp", response_model=TokenResponse)
def verify_otp(payload: VerifyOTPRequest, db: Session = Depends(get_db)):
    # TODO: verify against Firebase instead of the hardcoded dev OTP
    if payload.otp != DEV_OTP:
        raise HTTPException(status_code=400, detail="Invalid OTP")

    user = db.query(User).filter(User.phone_number == payload.phone_number).first()
    if not user:
        user = User(phone_number=payload.phone_number)
        db.add(user)
        db.commit()
        db.refresh(user)

    token = create_access_token(user.id)
    return TokenResponse(access_token=token)


# ---- Conversation routes ----
@app.get("/conversations")
def list_conversations(
    db: Session = Depends(get_db), user_id: int = Depends(get_current_user_id)
):
    rows = (
        db.query(ConversationParticipant)
        .filter(ConversationParticipant.user_id == user_id)
        .all()
    )
    return [{"conversation_id": row.conversation_id} for row in rows]


@app.post("/conversations")
def create_conversation(
    payload: ConversationCreate,
    db: Session = Depends(get_db),
    user_id: int = Depends(get_current_user_id),
):
    other = db.query(User).filter(User.phone_number == payload.participant_phone).first()
    if not other:
        raise HTTPException(status_code=404, detail="User not found")

    convo = Conversation(type=ConversationType.direct)
    db.add(convo)
    db.flush()

    db.add(ConversationParticipant(conversation_id=convo.id, user_id=user_id))
    db.add(ConversationParticipant(conversation_id=convo.id, user_id=other.id))
    db.commit()

    return {"conversation_id": convo.id}


@app.get("/conversations/{conversation_id}/messages", response_model=List[MessageOut])
def get_messages(
    conversation_id: int,
    before: Optional[int] = None,
    db: Session = Depends(get_db),
    user_id: int = Depends(get_current_user_id),
):
    query = db.query(Message).filter(Message.conversation_id == conversation_id)
    if before:
        query = query.filter(Message.id < before)
    return query.order_by(Message.id.desc()).limit(50).all()


# ---- Media route ----
@app.post("/media/upload")
async def upload_media(
    file: UploadFile = File(...), user_id: int = Depends(get_current_user_id)
):
    # TODO: stream `file` to Cloudflare R2 (or any S3-compatible bucket) and
    # return the public URL. Placeholder below so the endpoint shape is ready:
    return {"media_url": f"https://your-r2-bucket.example.com/{file.filename}"}


# ---- WebSocket ----
@app.websocket("/ws")
async def websocket_endpoint(websocket: WebSocket, token: str, db: Session = Depends(get_db)):
    try:
        payload = jwt.decode(token, JWT_SECRET, algorithms=[JWT_ALGORITHM])
        user_id = int(payload["sub"])
    except (JWTError, KeyError):
        await websocket.close(code=1008)
        return

    await manager.connect(user_id, websocket)
    try:
        while True:
            data = await websocket.receive_json()
            event = data.get("event")

            if event == "message:send":
                msg = Message(
                    conversation_id=data["conversation_id"],
                    sender_id=user_id,
                    content=data.get("content"),
                    message_type=data.get("message_type", "text"),
                    media_url=data.get("media_url"),
                )
                db.add(msg)
                db.commit()
                db.refresh(msg)

                recipients = (
                    db.query(ConversationParticipant)
                    .filter(
                        ConversationParticipant.conversation_id == data["conversation_id"],
                        ConversationParticipant.user_id != user_id,
                    )
                    .all()
                )
                for r in recipients:
                    await manager.send_to_user(
                        r.user_id,
                        {
                            "event": "message:new",
                            "id": msg.id,
                            "conversation_id": msg.conversation_id,
                            "sender_id": user_id,
                            "content": msg.content,
                            "message_type": msg.message_type.value,
                            "media_url": msg.media_url,
                        },
                    )

            elif event == "message:read":
                msg = db.query(Message).filter(Message.id == data["message_id"]).first()
                if msg:
                    msg.status = MessageStatus.read
                    db.commit()
                    await manager.send_to_user(
                        msg.sender_id,
                        {"event": "message:status", "message_id": msg.id, "status": "read"},
                    )

            elif event in ("typing:start", "typing:stop"):
                recipients = (
                    db.query(ConversationParticipant)
                    .filter(
                        ConversationParticipant.conversation_id == data["conversation_id"],
                        ConversationParticipant.user_id != user_id,
                    )
                    .all()
                )
                for r in recipients:
                    await manager.send_to_user(
                        r.user_id,
                        {
                            "event": event,
                            "conversation_id": data["conversation_id"],
                            "user_id": user_id,
                        },
                    )

    except WebSocketDisconnect:
        manager.disconnect(user_id)
```

Commit karne ke baad bata dena, main redeploy track kar lunga.
