# TODO: Check if this is the right place for this class
import logging
import pkg_resources


class ScanNotImplemented(Exception):
    pass


def get_manager(key, *args, **kwargs):
    """
    Gets a discovery manager class based on the specified key.

    Args:
        key (str): The key of the discovery scan to retrieve.
        *args: Positional arguments to pass to the manager class constructor.
        **kwargs: Keyword arguments to pass to the manager class constructor.

    Returns:
        The discovery manager class.

    Raises:
        ScanNotImplemented: If the specified key is not found in the registered
         scans.
    """
    entry_point_group = "udmi.network_discovery_scans"
    discovery_scans = {}

    for entry_point in pkg_resources.iter_entry_points(entry_point_group):
        try:
            discovery_scans[entry_point.name] = entry_point.load()
        except Exception as e:
            logging.error(f"Error loading entry point {entry_point.name}: {e}")

    manager = discovery_scans.get(key)

    if not manager:
        raise ScanNotImplemented(
            f"Invalid key, choose one from available scans: "
            f"{discovery_scans.keys()}")

    return manager(*args, **kwargs)
