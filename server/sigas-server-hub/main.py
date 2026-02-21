import argparse
import logging
import threading
import time

import waitress

from sigas_server_hub.actions.token_actions import TokenActions
from sigas_server_hub.actions.user_actions import UserActions
from sigas_server_hub.app_context import AppContext
from sigas_server_hub.apps import app_external, app_internal, set_app_context
from sigas_server_hub.actions.status_actions import StatusActions
from sigas_server_hub.actions.game_actions import GameActions
from sigas_server_hub.game.game_manager import GameManager
from sigas_server_hub.sessions import SessionManager
from sigas_server_hub.tokens import TokenManager
from sigas_server_hub.users import UserManager

parser = argparse.ArgumentParser(description="Sigas-Alpha hub")
parser.add_argument("--external-port", dest="external_port", default=None, required=True, help="External port to listen on")
parser.add_argument("--internal-port", dest="internal_port", default=None, required=True, help="Internal port to listen on")
parser.add_argument("--token-file", dest="token_file", default=None, required=True, help="File to persist tokens")
parser.add_argument("--users-file", dest="users_file", default=None, required=True, help="File to persist user details")
parser.add_argument("--expunge-trigger-ratio", dest="expunge_trigger_ratio", default=1, help="Expunge ratio for tokens")
parser.add_argument("--expunge-interval", dest="expunge_interval", default=60, help="Expunge interval in seconds for tokens")

parser.add_argument("-v", "--verbose", action="count", default=0)
parser.add_argument("-q", "--quiet", action="store_true")

args = parser.parse_args()

LOG_FORMAT = "%(asctime)s.%(msecs)d %(levelname)s: %(message)s"
# LOG_FORMAT = "[%(asctime)s] %(levelname)s [%(name)s.%(funcName)s:%(lineno)d] %(message)s"


LOG_LEVEL = logging.DEBUG


verbose = args.verbose + 2
if args.quiet: verbose = 0

if verbose == 0:
    LOG_LEVEL = logging.ERROR
elif verbose == 1:
    LOG_LEVEL = logging.WARNING
elif verbose == 2:
    LOG_LEVEL = logging.INFO
elif verbose == 3:
    LOG_LEVEL = logging.DEBUG


root_logger = logging.getLogger()
if len(root_logger.handlers) == 0:
    # Initialize the root logger only if it hasn't been done yet by a
    # parent module.
    logging.basicConfig(level=LOG_LEVEL, format=LOG_FORMAT, datefmt='%H:%M:%S')

logger = logging.getLogger("oauth2-proxy-controller")
logger.setLevel(LOG_LEVEL)


external_port = args.external_port
internal_port = args.internal_port

token_file = args.token_file
users_file = args.users_file


app_context = AppContext(token_file, users_file, args.expunge_trigger_ratio)
set_app_context(app_context)


logger.info(f"Starting server at port {external_port}...")
threading.Thread(
    target=lambda: waitress.serve(app_external, host="0.0.0.0", port=external_port), daemon=True
).start()
logger.info(f"Started server at port {external_port}.")

logger.info(f"Starting server at port {internal_port}...")
threading.Thread(
    target=lambda: waitress.serve(app_internal, host="0.0.0.0", port=internal_port), daemon=True
).start()
logger.info(f"Started server at port {internal_port}.")


expunge_interval = args.expunge_interval

last_expunge_checked = time.time()
while True:  # Add option for this to be completed
    time.sleep(1)
    if time.time() - last_expunge_checked >= expunge_interval:
        app_context.token_manager.check_for_expunge()
        app_context.user_manager.check_for_expunge()
        last_expunge_checked = time.time()
