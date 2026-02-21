import functools
import inspect
import random
import sys
from base64 import urlsafe_b64encode
from typing import Callable, Optional, Union, Tuple

from flask import Flask, request, Request

from sigas_server_hub.responses import error
from sigas_server_hub.web_actions import actions

HASH_LIMIT = 2 ** 48


app_external = Flask("External")
app_internal = Flask("Internal")

app_context: Optional['AppContext'] = None


def set_app_context(_app_context: 'AppContext') -> None:
    global app_context
    app_context = _app_context


def external_route(
        path: str,
        methods: list[str],
        json_body: str = None,
        headers: Union[str, list[Union[str, Tuple[str, str]]]] = None,
        params: Union[str, list[Union[str, Tuple[str, str]]]] = None):
    return route(app_external, path, methods, json_body, headers, params)


def internal_route(
        path: str,
        methods: list[str],
        json_body: str = None,
        headers: Union[str, list[Union[str, Tuple[str, str]]]] = None,
        params: Union[str, list[Union[str, Tuple[str, str]]]] = None):
    return route(app_internal, path, methods, json_body, headers, params)


def route(
        app: Flask,
        path: str,
        methods: list[str],
        json_body: str = None,
        headers: Union[str, list[Union[str, Tuple[str, str]]]] = None,
        params: Union[str, list[Union[str, Tuple[str, str]]]] = None):

    def decorator(func: Callable):
        class_placeholder = []

        @functools.wraps(func)
        def wrap(*args, **kwargs):
            if len(class_placeholder) == 0:
                class_placeholder.append(find_class(func))

            function_to_invoke = func
            cls = class_placeholder[0]
            if cls is not None:
                obj = actions[cls]
                function_to_invoke = functools.partial(function_to_invoke, obj)

            request_parameter: Optional[str] = None

            signature = inspect.signature(func)
            for parameter in signature.parameters.values():
                if Request == parameter.annotation:
                    request_parameter = parameter.name

            all_headers_param = headers if isinstance(headers, str) else None
            all_query_params = params if isinstance(params, str) else None

            def expand_param(v: Union[str, Tuple[str, str]]) -> str:
                return v if isinstance(v, str) else v[1]

            def header_value(v: Union[str, Tuple[str, str]]) -> Optional[str]:
                v = v if isinstance(v, str) else v[1]
                return request.headers[v] if v in request.headers else None

            def param_value(v: Union[str, Tuple[str, str]]) -> Optional[str]:
                v = v if isinstance(v, str) else v[1]
                return request.args[v] if v in request.args else None

            header_params = {expand_param(p): header_value(p) for p in headers} if headers is not None else {}
            query_params = {expand_param(p): param_value(p) for p in params} if params is not None else {}

            kwargs = {
                **kwargs,
                **header_params,
                **query_params,
                **({request_parameter: request} if request_parameter is not None else {}),
                **({json_body: request.json} if json_body is not None else {}),
                **({all_query_params: request.args} if all_query_params is not None else {}),
                **({all_headers_param: request.headers} if all_headers_param is not None else {})
            }

            return function_to_invoke(*args, **kwargs)

        app.add_url_rule(path, func.__name__, view_func=wrap, methods=methods)

        return wrap

    return decorator


def fast_random_hash(size: int) -> str:
    return urlsafe_b64encode((hash(random.random()) % HASH_LIMIT).to_bytes(size, "big")).decode("UTF8").rstrip("=")


def find_class(func):
    cls = sys.modules.get(func.__module__)
    if cls is None:
        return None
    if "__qualname__" not in dir(func):
        return None
    for name in func.__qualname__.split(".")[:-1]:
        cls = getattr(cls, name)
    if not inspect.isclass(cls):
        return None
    return cls


def permissions(permissions: list[Union[str, Tuple[str]]]):

    def decorator(func: Callable):

        @functools.wraps(func)
        def wrap(*args, **kwargs):

            if "Authorization" not in request.headers:
                return error(401, "Not authorised")

            authorisation = request.headers["Authorization"]
            if not authorisation.startswith("Token "):
                return error(401, "Not authorised; expecting 'Token' authorisation")

            token_str = authorisation[6:]

            token = app_context.token_manager.get_token(token_str)
            if token is None:
                return error(401, "Not authorised")

            if len(token.permissions & set(permissions)) == 0:
                return error(401, "Not authorised; not enough permissions")

            return func(*args, **kwargs)

        return wrap

    return decorator

