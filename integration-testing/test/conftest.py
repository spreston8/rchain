import sys

sys.path.insert(0, '.')

import os
import random
import pathlib
import tempfile
import contextlib
import collections
import dataclasses
from typing import List, TYPE_CHECKING, Generator

import pytest
import docker as docker_py

from rnode_testing.common import KeyPair
from rnode_testing.rnode import start_bootstrap
from rnode_testing.pregenerated_keypairs import PREGENERATED_KEYPAIRS

if TYPE_CHECKING:
    from docker.client import DockerClient


@dataclasses.dataclass
class CommandLineOptions:
    peer_count: int
    node_startup_timeout: int
    network_converge_timeout: int
    receive_timeout: int
    rnode_timeout: int
    blocks: int
    mount_dir: str


@dataclasses.dataclass
class TestingContext:
    peer_count: int
    node_startup_timeout: int
    network_converge_timeout: int
    receive_timeout: int
    rnode_timeout: int
    blocks: int
    mount_dir: str
    bonds_file: str
    bootstrap_keypair: str
    peers_keypairs: str
    docker: 'DockerClient'


def pytest_addoption(parser: "Parser") -> None:
    parser.addoption("--peer-count", action="store", default="2", help="number of peers in the network (excluding bootstrap node)")
    parser.addoption("--start-timeout", action="store", default="0", help="timeout in seconds for starting a node. Defaults to 30 + peer_count * 10")
    parser.addoption("--converge-timeout", action="store", default="0", help="timeout in seconds for network converge. Defaults to 200 + peer_count * 10")
    parser.addoption("--receive-timeout", action="store", default="0", help="timeout in seconds for receiving a message. Defaults to 10 + peer_count * 10")
    parser.addoption("--rnode-timeout", action="store", default="10", help="timeout in seconds for executing an rnode call (Examples: propose, show-logs etc.). Defaults to 10s")
    parser.addoption("--blocks", action="store", default="1", help="the number of deploys per test deploy")
    parser.addoption("--mount-dir", action="store", default=None, help="globally accesible directory for mounting between containers")


def make_timeout(peer_count, value, base, peer_factor=10):
    if value > 0:
        return value
    return base + peer_count * peer_factor


@pytest.yield_fixture(scope='session')
def command_line_options(request):
    peer_count = int(request.config.getoption("--peer-count"))
    start_timeout = int(request.config.getoption("--start-timeout"))
    converge_timeout = int(request.config.getoption("--converge-timeout"))
    receive_timeout = int(request.config.getoption("--receive-timeout"))
    rnode_timeout = int(request.config.getoption("--rnode-timeout"))
    blocks = int(request.config.getoption("--blocks"))
    mount_dir = request.config.getoption("--mount-dir")

    command_line_options = CommandLineOptions(
        peer_count=peer_count,
        node_startup_timeout=180,
        network_converge_timeout=make_timeout(peer_count, converge_timeout, 200, 10),
        receive_timeout=make_timeout(peer_count, receive_timeout, 10, 10),
        rnode_timeout=rnode_timeout,
        blocks=blocks,
        mount_dir=mount_dir,
    )

    yield command_line_options



@contextlib.contextmanager
def temporary_bonds_txt_file(validator_keys: List[KeyPair]) -> Generator[str, None, None]:
    (fd, file) = tempfile.mkstemp(prefix="rchain-bonds-file-", suffix=".txt")
    try:
        with os.fdopen(fd, "w") as f:
            for pair in validator_keys:
                bond = random.randint(1, 100)
                f.write("{} {}\n".format(pair.public_key, bond))
        yield file
    finally:
        os.unlink(file)


@pytest.yield_fixture(scope='session')
def docker_client() -> Generator["DockerClient", None, None]:
    docker_client = docker_py.from_env()
    try:
        yield docker_client
    finally:
        docker_client.volumes.prune()
        docker_client.networks.prune()


@contextlib.contextmanager
def testing_context(command_line_options, docker_client):
    # Using pre-generated validator key pairs by rnode. We do this because warning below  with python generated keys
    # WARN  coop.rchain.casper.Validate$ - CASPER: Ignoring block 2cb8fcc56e... because block creator 3641880481... has 0 weight
    validator_keys = [kp for kp in PREGENERATED_KEYPAIRS[0:context.peer_count+1]]
    with temporary_bonds_txt_file(validator_keys) as bonds_file:
        bootstrap_keypair = validator_keys[0]
        peers_keypairs=validator_keys[1:]

        context = TestingContext(
            bonds_file=bonds_file,
            bootstrap_keypair=bootstrap_keypair,
            peers_keypairs=peers_keypairs,
            docker=docker_client,
            **command_line_options,
        )

        yield context
