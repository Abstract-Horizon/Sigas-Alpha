import socket
import subprocess
import time
from concurrent.futures import ThreadPoolExecutor
from logging import getLogger
from pathlib import Path
from queue import Queue, Empty
from threading import Thread
from typing import Optional, Any, Generator

import requests

logger = getLogger(__name__)


class BrokerSetup:
    def __init__(self, server_port: Optional[int] = None, internal_port: Optional[int] = None, hub_port: Optional[int] = None) -> None:
        self.finished = False
        self.project_root = Path(__file__).parent.absolute()
        self.broker_home = (self.project_root.parent.parent.parent / "sigas-server-broker").absolute()
        self.jar_file = self.broker_home / "target" / "sigas-broker-0.0.1-SNAPSHOT.jar"

        self.server_port = server_port if server_port is not None else self._find_free_port()
        self.internal_port = internal_port if internal_port is not None else self._find_free_port()
        self.hub_port = hub_port if hub_port is not None else self._find_free_port()

        self.broker_process = None
        self.broker_in_thread = Thread(target=self._broker_in, daemon=True)

    def start(self) -> None:
        command = [
            "java", "-jar", str(self.jar_file),
            "--server-port", str(self.server_port),
            "--internal-port", str(self.internal_port),
            "--hub-url", f"http://localhost:{self.hub_port}"
        ]

        self.broker_process = subprocess.Popen(
            command,
            bufsize=0,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            shell=False,
            universal_newlines=True,
            cwd=str(self.broker_home)
        )

        self.broker_in_thread.start()

    def stop(self) -> None:
        try:
            requests.post(f"http://localhost:{self.internal_port}/stop")
        except:
            pass
        self.finished = True
        self.broker_in_thread.join(1)

    @staticmethod
    def _find_free_port() -> int:
        _socket = socket.socket()
        _socket.bind(('', 0))
        port = _socket.getsockname()[1]
        _socket.close()
        return port

    def _broker_in(self) -> None:
        try:
            return_code = self.broker_process.returncode
            while return_code is None and not self.finished:
                for out_line, err_line in self._read_popen_pipes(self.broker_process):
                    if err_line is not None and err_line != "":
                        print(f"Broker: {err_line}", end='' if err_line.endswith("\n") else '')
                    if out_line is not None and out_line != "":
                        print(f"Broker: {out_line}", end='' if err_line.endswith("\n") else '')
                    if out_line == "" and err_line == "":
                        time.sleep(0.25)
                return_code = self.broker_process.poll()
        finally:
            logger.warning("Finished broker loop")

    @staticmethod
    def _enqueue_output(file, queue: Queue) -> None:
        for _line in iter(file.readline, ''):
            queue.put(_line)
        file.close()

    def _read_popen_pipes(self, process) -> Generator[tuple[str | Any, str | Any], Any, None]:
        with ThreadPoolExecutor(2) as pool:
            q_stdout, q_stderr = Queue(), Queue()

            pool.submit(self._enqueue_output, process.stdout, q_stdout)
            pool.submit(self._enqueue_output, process.stderr, q_stderr)

            while True:
                if process.poll() is not None and q_stdout.empty() and q_stderr.empty():
                    break

                try:
                    stdout_line = q_stdout.get_nowait()
                except Empty:
                    stdout_line = ""

                try:
                    strerr_line = q_stderr.get_nowait()
                except Empty:
                    strerr_line = ""

                yield stdout_line, strerr_line
