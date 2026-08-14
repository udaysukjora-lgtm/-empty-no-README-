import os
import enum
from datetime import datetime, timedelta
from typing import Optional, List, Dict

from fastapi import (
    FastAPI, Depends, HTTPException, WebSocket, WebSocketDisconnect,
    UploadFile, File,
)
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import HTMLResponse
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


@app.get("/test", response_class=HTMLResponse)
def test_page():
    return """<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8" />
<meta name="viewport" content="width=device-width, initial-scale=1.0" />
<title>Backend Test</title>
<style>
  * { box-sizing: border-box; }
  body { background: #020617; color: #f1f5f9; font-family: -apple-system, system-ui, sans-serif; margin: 0; padding: 16px; }
  .wrap { max-width: 480px; margin: 0 auto; }
  h1 { font-size: 18px; margin: 0 0 2px; }
  .sub { font-size: 11px; color: #64748b; margin-bottom: 20px; }
  .panel { background: #0f172a; border: 1px solid #1e293b; border-radius: 10px; padding: 16px; margin-bottom: 12px; }
  .label { font-size: 11px; text-transform: uppercase; letter-spacing: 0.05em; color: #2dd4bf; font-family: monospace; }
  input { width: 100%; background: #020617; border: 1px solid #334155; border-radius: 6px; padding: 10px 12px; color: #f1f5f9; font-size: 14px; font-family: monospace; margin-top: 8px; margin-bottom: 8px; }
  button { width: 100%; background: #0d9488; border: none; border-radius: 6px; padding: 10px; color: white; font-size: 14px; font-weight: 600; margin-top: 4px; }
  button:disabled { opacity: 0.4; }
  .err { color: #fb7185; font-size: 12px; margin-top: 8px; line-height: 1.4; }
  .ok { color: #94a3b8; font-size: 13px; display: flex; align-items: center; gap: 6px; }
  .dot { width: 8px; height: 8px; border-radius: 50%; background: #2dd4bf; display: inline-block; }
  .dot.bad { background: #64748b; }
  .msgs { max-height: 160px; overflow-y: auto; margin: 10px 0; }
  .msg { background: #1e293b; padding: 6px 10px; border-radius: 8px; font-size: 13px; margin-bottom: 6px; max-width: 85%; }
  .msg.me { background: rgba(13,148,136,0.25); margin-left: auto; }
  .row { display: flex; gap: 8px; }
  .row input { margin: 0; flex: 1; }
  .row button { width: auto; padding: 10px 16px; margin: 0; }
  .hint { font-size: 12px; color: #475569; text-align: center; margin-top: 8px; }
</style>
</head>
<body>
<div class="wrap">
  <h1>Backend Test</h1>
  <div class="sub">same-origin test page</div>

  <div id="login-screen">
    <div class="panel">
      <div class="label">User A</div>
      <input id="phoneA" value="+911111111111" />
      <button id="sendOtpA" onclick="sendOtp('A')">Send OTP</button>
      <div id="otpBoxA" style="display:none">
        <input id="otpA" value="123456" />
        <button onclick="verifyOtp('A')">Verify</button>
      </div>
      <div id="errA" class="err"></div>
      <div id="doneA" class="ok" style="display:none"><span class="dot"></span>Logged in</div>
    </div>

    <div class="panel">
      <div class="label">User B</div>
      <input id="phoneB" value="+912222222222" />
      <button id="sendOtpB" onclick="sendOtp('B')">Send OTP</button>
      <div id="otpBoxB" style="display:none">
        <input id="otpB" value="123456" />
        <button onclick="verifyOtp('B')">Verify</button>
      </div>
      <div id="errB" class="err"></div>
      <div id="doneB" class="ok" style="display:none"><span class="dot"></span>Logged in</div>
    </div>

    <button id="startBtn" onclick="startConversation()" disabled>Conversation Shuru Karo</button>
    <div id="convoErr" class="err"></div>
    <div class="hint">Dono users OTP verify karo pehle</div>
  </div>

  <div id="chat-screen" style="display:none">
    <div class="panel">
      <div class="label"><span id="wsDotA" class="dot bad"></span> User A</div>
      <div id="msgsA" class="msgs"></div>
      <div class="row">
        <input id="draftA" placeholder="A ki taraf se likho..." onkeydown="if(event.key==='Enter')send('A')" />
        <button onclick="send('A')">Send</button>
      </div>
    </div>
    <div class="panel">
      <div class="label"><span id="wsDotB" class="dot bad"></span> User B</div>
      <div id="msgsB" class="msgs"></div>
      <div class="row">
        <input id="draftB" placeholder="B ki taraf se likho..." onkeydown="if(event.key==='Enter')send('B')" />
        <button onclick="send('B')">Send</button>
      </div>
    </div>
    <div class="hint">A se bhejo, B mein turant aana chahiye — aur ulta</div>
  </div>
</div>

<script>
const WS_URL = (location.protocol === 'https:' ? 'wss://' : 'ws://') + location.host + '/ws';
const state = { A: {}, B: {} };
let conversationId = null;

function decodeSub(token) {
  try {
    const b64 = token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/');
    return JSON.parse(atob(b64)).sub;
  } catch { return null; }
}

async function sendOtp(who) {
  const phone = document.getElementById('phone' + who).value;
  const errEl = document.getElementById('err' + who);
  errEl.textContent = '';
  try {
    const res = await fetch('/auth/send-otp', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ phone_number: phone }),
    });
    if (!res.ok) throw new Error('HTTP ' + res.status);
    document.getElementById('otpBox' + who).style.display = 'block';
    document.getElementById('sendOtp' + who).style.display = 'none';
  } catch (e) {
    errEl.textContent = 'Request fail hui: ' + e.message;
  }
}

async function verifyOtp(who) {
  const phone = document.getElementById('phone' + who).value;
  const otp = document.getElementById('otp' + who).value;
  const errEl = document.getElementById('err' + who);
  errEl.textContent = '';
  try {
    const res = await fetch('/auth/verify-otp', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ phone_number: phone, otp }),
    });
    if (!res.ok) throw new Error('HTTP ' + res.status);
    const data = await res.json();
    state[who].token = data.access_token;
    state[who].userId = decodeSub(data.access_token);
    state[who].phone = phone;
    document.getElementById('otpBox' + who).style.display = 'none';
    document.getElementById('done' + who).style.display = 'flex';
    checkBothReady();
  } catch (e) {
    errEl.textContent = 'Verify fail hui: ' + e.message;
  }
}

function checkBothReady() {
  if (state.A.token && state.B.token) {
    document.getElementById('startBtn').disabled = false;
  }
}

async function startConversation() {
  const errEl = document.getElementById('convoErr');
  errEl.textContent = '';
  try {
    const res = await fetch('/conversations', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Authorization: 'Bearer ' + state.A.token,
      },
      body: JSON.stringify({ participant_phone: state.B.phone }),
    });
    if (!res.ok) throw new Error('HTTP ' + res.status);
    const data = await res.json();
    conversationId = data.conversation_id;
    document.getElementById('login-screen').style.display = 'none';
    document.getElementById('chat-screen').style.display = 'block';
    connectWs('A');
    connectWs('B');
  } catch (e) {
    errEl.textContent = 'Conversation banane mein dikkat: ' + e.message;
  }
}

function connectWs(who) {
  const ws = new WebSocket(WS_URL + '?token=' + state[who].token);
  ws.onopen = () => { document.getElementById('wsDot' + who).classList.remove('bad'); };
  ws.onclose = () => { document.getElementById('wsDot' + who).classList.add('bad'); };
  ws.onerror = () => { document.getElementById('wsDot' + who).classList.add('bad'); };
  ws.onmessage = (evt) => {
    try {
      const data = JSON.parse(evt.data);
      if (data.event === 'message:new') {
        addBubble(who, data.content, false);
      }
    } catch {}
  };
  state[who].ws = ws;
}

function addBubble(who, text, self) {
  const el = document.createElement('div');
  el.className = 'msg' + (self ? ' me' : '');
  el.textContent = text;
  const container = document.getElementById('msgs' + who);
  container.appendChild(el);
  container.scrollTop = container.scrollHeight;
}

function send(who) {
  const input = document.getElementById('draft' + who);
  const text = input.value.trim();
  if (!text) return;
  const ws = state[who].ws;
  if (ws && ws.readyState === WebSocket.OPEN) {
    ws.send(JSON.stringify({
      event: 'message:send',
      conversation_id: conversationId,
      content: text,
      message_type: 'text',
    }));
    addBubble(who, text, true);
    input.value = '';
  }
}
</script>
</body>
</html>
"""


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
