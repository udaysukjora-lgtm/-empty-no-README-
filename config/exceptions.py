from rest_framework.views import exception_handler


def gateway_exception_handler(exc, context):
    response = exception_handler(exc, context)
    if response is not None:
        response.data = {
            "error": {
                "message": response.data if isinstance(response.data, str) else response.data,
                "status_code": response.status_code,
            }
        }
    return response
