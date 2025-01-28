import udmi4py.client.manager.base as base
from udmi.schema.state_discovery import DiscoveryState


class DiscoveryManager(base.Manager):
    def update_state(self, state: object) -> None:
        pass
