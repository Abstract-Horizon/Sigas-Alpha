import argparse
import logging
import threading
import time

import waitress

from sigas_server_hub.game.kubernetes_game_manager import KubernetesGameManager
from sigas_server_hub.game.test_game_manager import TestGameManager
from sigas_server_hub.sigas_hub import SigasHub
from sigas_server_hub.flask_apps import app_external, app_internal, set_hub

parser = argparse.ArgumentParser(description="Sigas-Alpha hub")
parser.add_argument("--external-port", dest="external_port", default=None, required=True, help="External port to listen on")
parser.add_argument("--internal-port", dest="internal_port", default=None, required=True, help="Internal port to listen on")
parser.add_argument("--token-file", dest="token_file", default=None, required=True, help="File to persist tokens")
parser.add_argument("--users-file", dest="users_file", default=None, required=True, help="File to persist user details")
parser.add_argument("--expunge-trigger-ratio", dest="expunge_trigger_ratio", default=1, help="Expunge ratio for tokens")
parser.add_argument("--expunge-interval", dest="expunge_interval", default=60, help="Expunge interval in seconds for tokens")

parser.add_argument("--test-setup", action="store_true")

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

expunge_interval = args.expunge_interval

sigas_hub = SigasHub(
    app_external, app_internal,
    external_port, internal_port,
    token_file=token_file,
    users_file=users_file,
    expunge_trigger_ratio=args.expunge_trigger_ratio,
    expunge_interval=expunge_interval,
    game_manager_class=TestGameManager if args.test_setup else KubernetesGameManager)

set_hub(sigas_hub)


sigas_hub.start()
