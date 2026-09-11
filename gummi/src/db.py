"""Database access adapter for GUMMI querying Butler, Barbican, and UUFI MCP endpoints or mock mode."""

from datetime import datetime, timezone, timedelta
import json
import os
import sys
import time
from typing import Any, Dict, List, Optional, Tuple, Union

from mcp.butler.client import ButlerClient
from mcp.barbican.client import BarbicanClient
from mcp.uufi.client import UUFIClient
class GummiDB:
    """Manages read and query operations by delegating exclusively to Butler, Barbican, and UUFI MCP services."""

    def __init__(
        self,
        butler_client: Optional[Any] = None,
        barbican_client: Optional[Any] = None,
        uufi_client: Optional[Any] = None,
        butler_port: Optional[int] = None,
        barbican_port: Optional[int] = None,
        uufi_port: Optional[int] = None,
        butler_endpoint: Optional[str] = None,
        barbican_endpoint: Optional[str] = None,
        uufi_endpoint: Optional[str] = None,
        mock_mode: bool = False,
        **kwargs,
    ):
        self.mock_mode = mock_mode
        self.butler_port = butler_port or 8088
        self.barbican_port = barbican_port or 8085
        self.uufi_port = uufi_port or 8087
        self.butler_endpoint = butler_endpoint
        self.barbican_endpoint = barbican_endpoint
        self.uufi_endpoint = uufi_endpoint

        if mock_mode:
            self.butler = None
            self.barbican = None
            self.uufi = None
            self._mock_fleet = self._generate_mock_fleet()
            self._mock_messages = self._generate_mock_messages()
            self._mock_rollouts = self._generate_mock_rollouts()
            self._mock_rollout_id_counter = len(self._mock_rollouts) + 1
        else:
            self.butler = butler_client or ButlerClient(
                endpoint=self.butler_endpoint,
                port=self.butler_port if not self.butler_endpoint else None,
            )
            self.barbican = barbican_client or BarbicanClient(
                endpoint=self.barbican_endpoint,
                port=self.barbican_port if not self.barbican_endpoint else None,
            )
            self.uufi = uufi_client or UUFIClient(
                endpoint=self.uufi_endpoint,
                port=self.uufi_port if not self.uufi_endpoint else None,
            )
            self._mock_fleet = []
            self._mock_messages = {}
            self._mock_rollouts = {}
            self._mock_rollout_id_counter = 1

    # --------------------------------------------------------------------------
    # Health & Connectivity
    # --------------------------------------------------------------------------

    def check_component_health(self) -> Dict[str, Any]:
        """Probes backend MCP endpoints to report latency and status."""
        if self.mock_mode:
            components = {
                "postgres": {
                    "status": "MOCK_MODE",
                    "endpoint": "mock://postgres",
                    "latency_ms": 0.0,
                    "note": "Running in mock mode",
                },
                "influxdb": {
                    "status": "MOCK_MODE",
                    "endpoint": "mock://influxdb",
                    "latency_ms": 0.0,
                    "note": "Running in mock mode",
                },
                "uufi_service": {
                    "status": "MOCK_MODE",
                    "endpoint": "mock://uufi",
                    "latency_ms": 0.0,
                    "note": "Running in mock mode",
                },
                "mqtt_broker": {
                    "status": "MOCK_MODE",
                    "endpoint": "mock://uufi",
                    "latency_ms": 0.0,
                    "note": "Running in mock mode",
                },
                "etcd": {
                    "status": "MOCK_MODE",
                    "endpoint": "mock://etcd",
                    "latency_ms": 0.0,
                    "note": "Running in mock mode",
                },
                "barbican": {
                    "status": "MOCK_MODE",
                    "endpoint": "mock://barbican",
                    "latency_ms": 0.0,
                    "note": "Running in mock mode",
                },
                "butler": {
                    "status": "MOCK_MODE",
                    "endpoint": "mock://butler",
                    "latency_ms": 0.0,
                    "note": "Running in mock mode",
                },
            }
            return {
                "overall_status": "MOCK_MODE",
                "timestamp": datetime.now(timezone.utc).isoformat(),
                "components": components,
            }

        components: Dict[str, Any] = {}

        # 1. Butler MCP Probe (Relational & Timeseries Datastores)
        t0 = time.perf_counter()
        try:
            if not self.butler:
                raise ConnectionError("Butler MCP client not configured")
            butler_h = self.butler.health()
            butler_lat = round((time.perf_counter() - t0) * 1000, 2)
            butler_status = butler_h.get("status", "DOWN")
            datastores = butler_h.get("datastores", {})
            pg_up = datastores.get("relational", False)
            influx_up = datastores.get("timeseries", False)

            components["butler"] = {
                "status": "UP" if butler_h.get("connected") else butler_status,
                "endpoint": getattr(self.butler, "endpoint", f"127.0.0.1:{self.butler_port}"),
                "latency_ms": butler_lat,
            }
            components["postgres"] = {
                "status": "UP" if pg_up else "DOWN",
                "endpoint": "butler://relational",
                "latency_ms": butler_lat,
            }
            components["influxdb"] = {
                "status": "UP" if influx_up else "DOWN",
                "endpoint": "butler://timeseries",
                "latency_ms": butler_lat,
            }
        except Exception as e:
            components["butler"] = {
                "status": "DOWN",
                "endpoint": getattr(self.butler, "endpoint", f"127.0.0.1:{self.butler_port}") if self.butler else "unconfigured",
                "error": str(e),
            }
            components["postgres"] = {
                "status": "DOWN",
                "endpoint": "butler://relational",
                "error": f"Butler MCP unreachable: {e}",
            }
            components["influxdb"] = {
                "status": "DOWN",
                "endpoint": "butler://timeseries",
                "error": f"Butler MCP unreachable: {e}",
            }

        # 2. Barbican MCP Probe (Device Catalog & etcd)
        t0 = time.perf_counter()
        try:
            if not self.barbican:
                raise ConnectionError("Barbican MCP client not configured")
            barbican_h = self.barbican.health()
            barbican_lat = round((time.perf_counter() - t0) * 1000, 2)
            is_connected = barbican_h.get("connected", False)

            components["barbican"] = {
                "status": "UP" if is_connected else barbican_h.get("status", "DOWN"),
                "endpoint": getattr(self.barbican, "endpoint", f"127.0.0.1:{self.barbican_port}"),
                "latency_ms": barbican_lat,
            }
            components["etcd"] = {
                "status": "UP" if is_connected else "DOWN",
                "endpoint": "barbican://etcd",
                "latency_ms": barbican_lat,
            }
        except Exception as e:
            components["barbican"] = {
                "status": "DOWN",
                "endpoint": getattr(self.barbican, "endpoint", f"127.0.0.1:{self.barbican_port}") if self.barbican else "unconfigured",
                "error": str(e),
            }
            components["etcd"] = {
                "status": "DOWN",
                "endpoint": "barbican://etcd",
                "error": f"Barbican MCP unreachable: {e}",
            }

        # 3. UUFI MCP Probe (Messaging Fabric & Broker)
        t0 = time.perf_counter()
        try:
            if not self.uufi:
                raise ConnectionError("UUFI MCP client not configured")
            uufi_h = self.uufi.health()
            uufi_lat = round((time.perf_counter() - t0) * 1000, 2)
            uufi_up = uufi_h.get("status") in ("UP", "REACHABLE", "ACTIVE")
            broker_info = uufi_h.get("broker", "uufi-broker")

            components["uufi_service"] = {
                "status": "UP" if uufi_up else "DOWN",
                "endpoint": getattr(self.uufi, "endpoint", f"127.0.0.1:{self.uufi_port}"),
                "latency_ms": uufi_lat,
            }
            components["mqtt_broker"] = {
                "status": "UP" if uufi_up else "DOWN",
                "endpoint": broker_info,
                "latency_ms": uufi_lat,
            }
        except Exception as e:
            components["uufi_service"] = {
                "status": "DOWN",
                "endpoint": getattr(self.uufi, "endpoint", f"127.0.0.1:{self.uufi_port}") if self.uufi else "unconfigured",
                "error": str(e),
            }
            components["mqtt_broker"] = {
                "status": "DOWN",
                "endpoint": "uufi://broker",
                "error": f"UUFI MCP unreachable: {e}",
            }

        all_up = all(c.get("status") == "UP" for c in components.values())
        overall = "HEALTHY" if all_up else "DEGRADED"
        return {
            "overall_status": overall,
            "timestamp": datetime.now(timezone.utc).isoformat(),
            "components": components,
        }

    # --------------------------------------------------------------------------
    # Portfolio Overview Queries
    # --------------------------------------------------------------------------

    def get_portfolio_summary(self) -> Dict[str, Any]:
        """Returns aggregate device counts, online/offline breakdown, and recent alerts."""
        if self.mock_mode:
            return self._mock_portfolio_summary()

        if not self.butler:
            raise ConnectionError("Butler MCP client is not configured or unavailable")
        try:
            return self.butler.get_portfolio_summary()
        except Exception as e:
            raise ConnectionError(f"Butler MCP request failed: {e}") from e

    def get_alerts(self, limit: int = 50, min_level: int = 500) -> List[Dict[str, Any]]:
        """Queries recent validation and alarm events."""
        if self.mock_mode:
            return self._mock_alerts(limit=limit, min_level=min_level)

        if not self.butler:
            raise ConnectionError("Butler MCP client is not configured or unavailable")
        try:
            return self.butler.get_alerts(limit=limit, min_level=min_level)
        except Exception as e:
            raise ConnectionError(f"Butler MCP request failed: {e}") from e

    # --------------------------------------------------------------------------
    # Devices Explorer Queries
    # --------------------------------------------------------------------------

    def get_devices(
        self,
        limit: int = 100,
        offset: int = 0,
        registry_id: Optional[str] = None,
        device_prefix: Optional[str] = None,
        make: Optional[str] = None,
        model: Optional[str] = None,
        status: Optional[str] = None,
        search: Optional[str] = None,
    ) -> Dict[str, Any]:
        """Fetches a paginated, filtered list of devices via Butler MCP."""
        if self.mock_mode:
            return self._filter_mock_devices(
                limit=limit,
                offset=offset,
                registry_id=registry_id,
                device_prefix=device_prefix,
                make=make,
                model=model,
                status=status,
                search=search,
            )

        if not self.butler:
            raise ConnectionError("Butler MCP client is not configured or unavailable")
        try:
            return self.butler.get_devices(
                limit=limit,
                offset=offset,
                registry_id=registry_id,
                device_prefix=device_prefix,
                make=make,
                model=model,
                status=status,
                search=search,
            )
        except Exception as e:
            raise ConnectionError(f"Butler MCP request failed: {e}") from e

    # --------------------------------------------------------------------------
    # Device Detail & Telemetry Queries
    # --------------------------------------------------------------------------

    def get_device_detail(self, registry_id: str, device_id: str) -> Optional[Dict[str, Any]]:
        """Returns metadata, system state, point states, and recent validation errors for a device."""
        if self.mock_mode:
            return self._mock_device_detail(registry_id, device_id)

        if not self.butler:
            raise ConnectionError("Butler MCP client is not configured or unavailable")
        try:
            return self.butler.get_device_detail(registry_id, device_id)
        except Exception as e:
            raise ConnectionError(f"Butler MCP request failed: {e}") from e

    def get_device_telemetry(
        self,
        registry_id: str,
        device_id: str,
        point_names: List[str],
        start: str = "-1h",
        stop: str = "now()",
    ) -> Dict[str, Any]:
        """Queries Butler MCP for time-series point values."""
        if self.mock_mode:
            return self._mock_telemetry(registry_id, device_id, point_names)

        if not self.butler:
            raise ConnectionError("Butler MCP client is not configured or unavailable")
        try:
            return self.butler.get_device_telemetry(
                registry_id=registry_id,
                device_id=device_id,
                point_names=point_names,
                start=start,
                stop=stop,
            )
        except Exception as e:
            raise ConnectionError(f"Butler MCP request failed: {e}") from e

    # --------------------------------------------------------------------------
    # Message Lifecycle & Mapping Queries (Model -> Discovery -> Proposal)
    # --------------------------------------------------------------------------

    def get_device_messages(
        self,
        registry_id: str,
        device_id: str,
    ) -> List[Dict[str, Any]]:
        """Queries Butler MCP for all lifecycle messages (model, discovery, propose) for a device."""
        if self.mock_mode:
            return self._mock_device_messages(registry_id, device_id)

        if not self.butler:
            raise ConnectionError("Butler MCP client is not configured or unavailable")
        try:
            return self.butler.get_device_messages(registry_id, device_id)
        except Exception as e:
            raise ConnectionError(f"Butler MCP request failed: {e}") from e

    def populate_mapping_scenario(
        self,
        registry_id: str = "ZZ-TRI-FECTA",
    ) -> Dict[str, Any]:
        """Populates the database or mock store with original models, discovery events, and generated proposals."""
        now = datetime.now(timezone.utc)
        t_model = (now - timedelta(days=3)).strftime("%Y-%m-%dT%H:%M:%SZ")
        t_disc = (now - timedelta(minutes=30)).strftime("%Y-%m-%dT%H:%M:%SZ")
        t_prop = (now - timedelta(minutes=10)).strftime("%Y-%m-%dT%H:%M:%SZ")

        # 1. Base Model for AHU-22
        model_payload = {
            "version": "1.5.7",
            "timestamp": t_model,
            "system": {
                "location": {"site": "US-SFO-XYY", "room": "Room-204", "floor": "Floor-2"},
                "serial_no": "SN-AHU-22",
                "hardware": {"make": "Acme Controls", "model": "HVAC-3000"},
            },
            "localnet": {
                "families": {
                    "vendor": {"addr": "0x65"}
                }
            },
            "pointset": {
                "points": {
                    "supply_air_temperature_sensor": {"units": "Degrees-Celsius"}
                }
            }
        }

        # 2. Discovery Event from GAT-123 discovering new vendor address and bacnet address
        discovery_payload = {
            "timestamp": t_disc,
            "version": "1.5.7",
            "generation": t_disc,
            "family": "vendor",
            "addr": "0x68",
            "families": {
                "vendor": {"addr": "0x68"},
                "bacnet": {"addr": "10022"},
                "ipv4": {"addr": "192.168.1.122"}
            }
        }

        # 3. Reconciled Proposal generated by mapper
        proposal_localnet = {
            "version": "1.5.7",
            "timestamp": t_prop,
            "updateFrom": t_model,
            "source": "butler",
            "transactionId": "TXN-map-01",
            "families": {
                "vendor": {"addr": "0x68"},
                "bacnet": {"addr": "10022"},
                "ipv4": {"addr": "192.168.1.122"}
            }
        }

        proposal_pointset = {
            "version": "1.5.7",
            "timestamp": t_prop,
            "updateFrom": t_model,
            "source": "butler",
            "transactionId": "TXN-map-02",
            "points": {
                "supply_air_temperature_sensor": {"units": "Degrees-Celsius"},
                "return_air_temperature_sensor": {"ref": "point_ret_temp"}
            }
        }

        records = [
            {
                "timestamp": t_model,
                "registry_id": registry_id,
                "device_id": "AHU-22",
                "sub_type": "model",
                "sub_folder": "system",
                "payload": model_payload,
                "attributes": {"source": "registrar"},
            },
            {
                "timestamp": t_disc,
                "registry_id": registry_id,
                "device_id": "GAT-123",
                "sub_type": "events",
                "sub_folder": "discovery",
                "payload": discovery_payload,
                "attributes": {"source": "pubber", "gatewayId": "GAT-123"},
            },
            {
                "timestamp": t_prop,
                "registry_id": registry_id,
                "device_id": "AHU-22",
                "sub_type": "propose",
                "sub_folder": "localnet",
                "payload": proposal_localnet,
                "attributes": {"source": "butler", "updateFrom": t_model, "transactionId": "TXN-map-01"},
            },
            {
                "timestamp": t_prop,
                "registry_id": registry_id,
                "device_id": "AHU-22",
                "sub_type": "propose",
                "sub_folder": "pointset",
                "payload": proposal_pointset,
                "attributes": {"source": "butler", "updateFrom": t_model, "transactionId": "TXN-map-02"},
            },
        ]

        if not self.mock_mode:
            if not self.butler:
                raise ConnectionError("Butler MCP client is not configured or unavailable")
            try:
                for r in records:
                    self.butler.record_message(
                        registry_id=r["registry_id"],
                        device_id=r["device_id"],
                        sub_type=r["sub_type"],
                        sub_folder=r["sub_folder"],
                        payload=r["payload"],
                        timestamp=r["timestamp"],
                    )
                messages = self.butler.get_device_messages(registry_id, "AHU-22")
                return {
                    "status": "SUCCESS",
                    "registry_id": registry_id,
                    "device_id": "AHU-22",
                    "records_inserted": len(records),
                    "messages": messages,
                }
            except Exception as e:
                raise ConnectionError(f"Failed to populate mapping scenario via Butler MCP: {e}") from e

        # Mock mode store
        key = (registry_id, "AHU-22")
        self._mock_messages[key] = [
            {
                "id": idx + 1,
                "timestamp": r["timestamp"],
                "registry_id": r["registry_id"],
                "device_id": r["device_id"],
                "sub_type": r["sub_type"],
                "sub_folder": r["sub_folder"],
                "payload": r["payload"],
                "updateFrom": r["attributes"].get("updateFrom"),
                "source": r["attributes"].get("source"),
                "transaction_id": r["attributes"].get("transactionId"),
            }
            for idx, r in enumerate(records)
        ]

        return {
            "status": "SUCCESS",
            "registry_id": registry_id,
            "device_id": "AHU-22",
            "records_inserted": len(records),
            "messages": self._mock_messages[key],
        }

    # --------------------------------------------------------------------------
    # Managed Rollouts Operations (Delegated to Butler MCP)
    # --------------------------------------------------------------------------

    def create_rollout(
        self,
        name: str,
        target_filter: Dict[str, Any],
        target_payload: Dict[str, Any],
        target_subfolder: str = "system",
        batch_size: int = 10,
        batch_interval_sec: int = 60,
        total_devices: int = 0,
    ) -> Dict[str, Any]:
        """Creates a staged configuration rollout campaign via Butler."""
        if self.mock_mode:
            r_id = self._mock_rollout_id_counter
            self._mock_rollout_id_counter += 1
            rollout = {
                "id": r_id,
                "name": name,
                "target_filter": target_filter,
                "target_subfolder": target_subfolder,
                "target_payload": target_payload,
                "status": "RUNNING",
                "batch_size": batch_size,
                "batch_interval_sec": batch_interval_sec,
                "total_devices": total_devices,
                "converged_devices": 0,
                "failed_devices": 0,
                "created_at": datetime.now(timezone.utc).isoformat(),
            }
            self._mock_rollouts[r_id] = rollout
            return rollout

        if not self.butler:
            raise ConnectionError("Butler MCP client is not configured or unavailable")
        try:
            return self.butler.create_rollout(
                name=name,
                target_filter=target_filter,
                target_payload=target_payload,
                target_subfolder=target_subfolder,
                batch_size=batch_size,
                batch_interval_sec=batch_interval_sec,
                total_devices=total_devices,
            )
        except Exception as e:
            raise ConnectionError(f"Butler MCP request failed: {e}") from e

    def list_rollouts(self) -> List[Dict[str, Any]]:
        """Returns all rollout campaigns from Butler."""
        if self.mock_mode:
            return list(self._mock_rollouts.values())

        if not self.butler:
            raise ConnectionError("Butler MCP client is not configured or unavailable")
        try:
            return self.butler.list_rollouts()
        except Exception as e:
            raise ConnectionError(f"Butler MCP request failed: {e}") from e

    def get_rollout(self, rollout_id: int) -> Optional[Dict[str, Any]]:
        """Returns a single rollout campaign from Butler."""
        if self.mock_mode:
            return self._mock_rollouts.get(rollout_id)

        if not self.butler:
            raise ConnectionError("Butler MCP client is not configured or unavailable")
        try:
            return self.butler.get_rollout(rollout_id)
        except Exception as e:
            raise ConnectionError(f"Butler MCP request failed: {e}") from e

    def update_rollout(
        self,
        rollout_id: int,
        status: Optional[str] = None,
        converged_devices: Optional[int] = None,
        failed_devices: Optional[int] = None,
    ) -> Optional[Dict[str, Any]]:
        """Updates rollout status (e.g. PAUSED, CANCELLED) or progress in Butler."""
        if self.mock_mode:
            if rollout_id not in self._mock_rollouts:
                return None
            rollout = self._mock_rollouts[rollout_id]
            if status:
                s_lower = status.lower()
                if s_lower == "pause":
                    rollout["status"] = "PAUSED"
                elif s_lower == "cancel":
                    rollout["status"] = "CANCELLED"
                else:
                    rollout["status"] = status.upper()
            if converged_devices is not None:
                rollout["converged_devices"] = converged_devices
                if rollout["converged_devices"] >= rollout["total_devices"]:
                    rollout["status"] = "COMPLETED"
            if failed_devices is not None:
                rollout["failed_devices"] = failed_devices
            rollout["updated_at"] = datetime.now(timezone.utc).isoformat()
            return rollout

        if not self.butler:
            raise ConnectionError("Butler MCP client is not configured or unavailable")
        try:
            return self.butler.update_rollout(
                rollout_id=rollout_id,
                status=status,
                converged_devices=converged_devices,
                failed_devices=failed_devices,
            )
        except Exception as e:
            raise ConnectionError(f"Butler MCP request failed: {e}") from e

    # --------------------------------------------------------------------------
    # Barbican / ETCD Catalog Explorer Queries
    # --------------------------------------------------------------------------

    def get_registries(self, prefix: str = "/r/") -> Dict[str, Any]:
        """Fetches unique UDMI registries and total registered devices from Barbican MCP."""
        if self.mock_mode:
            regs = sorted(list({d["registry_id"] for d in self._mock_fleet}))
            if not regs:
                regs = ["AA-LON-TEST", "AA-MSQ-TEST", "UDMI-REFLECT", "US-MTV-1758", "ZZ-TRI-FECTA"]
            if prefix and prefix != "/r/":
                clean_prefix = prefix[3:] if prefix.startswith("/r/") else prefix
                regs = [r for r in regs if r.startswith(clean_prefix)]
            total_devs = len(self._mock_fleet) if self._mock_fleet else 10
            return {
                "registries": regs,
                "totalDevicesCount": total_devs,
            }

        if not self.barbican:
            raise ConnectionError("Barbican MCP client is not configured or unavailable")
        try:
            return self.barbican.list_registries(prefix=prefix)
        except Exception as e:
            raise ConnectionError(f"Barbican MCP request failed: {e}") from e

    def get_registry_devices(self, registry_id: str) -> Dict[str, Any]:
        """Fetches list of devices for a specific registry from Barbican MCP."""
        if self.mock_mode:
            devs = sorted([d["device_id"] for d in self._mock_fleet if d.get("registry_id") == registry_id])
            if not devs and registry_id == "ZZ-TRI-FECTA":
                devs = ["AHU-1", "AHU-22", "GAT-123", "SNS-4"]
            return {
                "registryId": registry_id,
                "devices": devs,
            }

        if not self.barbican:
            raise ConnectionError("Barbican MCP client is not configured or unavailable")
        try:
            return self.barbican.list_devices(registry_id=registry_id)
        except Exception as e:
            raise ConnectionError(f"Barbican MCP request failed: {e}") from e

    def get_device_etcd_properties(self, registry_id: str, device_id: str) -> Dict[str, Any]:
        """Fetches raw key-value properties for a device in a registry from Barbican MCP."""
        if self.mock_mode:
            return self._mock_device_etcd_properties(registry_id, device_id)

        if not self.barbican:
            raise ConnectionError("Barbican MCP client is not configured or unavailable")
        try:
            return self.barbican.get_device_properties(registry_id=registry_id, device_id=device_id)
        except Exception as e:
            raise ConnectionError(f"Barbican MCP request failed: {e}") from e

    # --------------------------------------------------------------------------
    # Mock Data Generators
    # --------------------------------------------------------------------------

    def _generate_mock_rollouts(self) -> Dict[int, Dict[str, Any]]:
        """Generates initial mock staged rollout campaigns."""
        now = datetime.now(timezone.utc).isoformat()
        return {
            1: {
                "id": 1,
                "name": "Upgrade AHU Fleet",
                "target_filter": {"make": "Acme Controls"},
                "target_subfolder": "system",
                "target_payload": {"system": {"software": {"system": "2.5.0"}}},
                "status": "RUNNING",
                "batch_size": 5,
                "batch_interval_sec": 60,
                "total_devices": 10,
                "converged_devices": 3,
                "failed_devices": 0,
                "created_at": now,
            }
        }

    def _generate_mock_fleet(self) -> List[Dict[str, Any]]:
        """Generates realistic mock device catalog."""
        def _iso_z(dt: Optional[datetime] = None) -> str:
            d = dt or datetime.now(timezone.utc)
            return d.strftime("%Y-%m-%dT%H:%M:%SZ")

        devices = []
        registries = ["ZZ-TRI-FECTA", "US-MTV-1", "US-SFO-2"]
        makes_models = [
            ("Acme Controls", "HVAC-3000", "2.4.1"),
            ("Carrier", "WeatherMaster-500", "3.1.0"),
            ("Trane", "IntelliPak-2", "1.9.4"),
            ("Siemens", "Desigo-CC-40", "4.2.0"),
            ("Johnson Controls", "Metasys-FEC", "2.0.8"),
            ("Schneider Electric", "EcoStruxure-9", "3.0.2"),
        ]

        # 1. Primary Air Handling Units & Gateways (AHU & GAT)
        devices.append({
            "id": len(devices) + 1,
            "registry_id": "ZZ-TRI-FECTA",
            "device_id": "AHU-22",
            "make": "Acme Controls",
            "model": "HVAC-3000",
            "serial_no": "SN-AHU-22",
            "software_version": "2.4.1",
            "liveness_status": "ONLINE",
            "last_seen": _iso_z(),
        })
        devices.append({
            "id": len(devices) + 1,
            "registry_id": "ZZ-TRI-FECTA",
            "device_id": "GAT-123",
            "make": "Siemens",
            "model": "Desigo-CC-40",
            "serial_no": "SN-GAT-123",
            "software_version": "4.2.0",
            "liveness_status": "ONLINE",
            "last_seen": _iso_z(),
        })
        for i in range(1, 7):
            mm = makes_models[(i - 1) % len(makes_models)]
            devices.append({
                "id": len(devices) + 1,
                "registry_id": "ZZ-TRI-FECTA",
                "device_id": f"AHU-{i}",
                "make": mm[0],
                "model": mm[1],
                "serial_no": f"SN-AHU-990{i}",
                "software_version": mm[2],
                "liveness_status": "ONLINE" if i != 4 else "ERROR",
                "last_seen": _iso_z(datetime.now(timezone.utc) - timedelta(minutes=i * 2)),
            })

        # 2. Variable Air Volume Boxes (VAV)
        for i in range(101, 116):
            mm = makes_models[i % len(makes_models)]
            devices.append({
                "id": len(devices) + 1,
                "registry_id": "ZZ-TRI-FECTA" if i < 110 else "US-MTV-1",
                "device_id": f"VAV-{i}",
                "make": mm[0],
                "model": f"VAV-Box-{i}",
                "serial_no": f"SN-VAV-{i}X",
                "software_version": mm[2],
                "liveness_status": "ONLINE" if i != 108 else "OFFLINE",
                "last_seen": _iso_z(datetime.now(timezone.utc) - timedelta(minutes=(i % 10) * 3 + 1)),
            })

        # 3. Chillers and Central Plant (CHILLER, PUMP, BOILER)
        for i in range(1, 5):
            devices.append({
                "id": len(devices) + 1,
                "registry_id": "US-SFO-2",
                "device_id": f"CHILLER-{i}",
                "make": "Trane",
                "model": "Centravac-WaterCooled",
                "serial_no": f"SN-CHIL-{i}00",
                "software_version": "5.0.1",
                "liveness_status": "ONLINE",
                "last_seen": _iso_z(datetime.now(timezone.utc) - timedelta(minutes=i)),
            })
            devices.append({
                "id": len(devices) + 1,
                "registry_id": "US-SFO-2",
                "device_id": f"PUMP-{i}",
                "make": "Grundfos",
                "model": "Magna3-VFD",
                "serial_no": f"SN-PUMP-{i}99",
                "software_version": "2.1.0",
                "liveness_status": "ONLINE",
                "last_seen": _iso_z(datetime.now(timezone.utc) - timedelta(minutes=i + 3)),
            })

        # 4. Lighting & Power Meters
        for i in range(1, 5):
            devices.append({
                "id": len(devices) + 1,
                "registry_id": "US-MTV-1",
                "device_id": f"LIGHTING-CTR-{i}",
                "make": "Lutron",
                "model": "Quantum-Hub",
                "serial_no": f"SN-LUT-{i}00",
                "software_version": "3.8.2",
                "liveness_status": "ONLINE",
                "last_seen": _iso_z(datetime.now(timezone.utc) - timedelta(minutes=4)),
            })
            devices.append({
                "id": len(devices) + 1,
                "registry_id": "US-MTV-1",
                "device_id": f"METER-PWR-{i}",
                "make": "Schneider Electric",
                "model": "PowerLogic-ION9000",
                "serial_no": f"SN-MET-{i}55",
                "software_version": "1.4.0",
                "liveness_status": "ONLINE" if i != 3 else "OFFLINE",
                "last_seen": _iso_z(datetime.now(timezone.utc) - timedelta(minutes=i * 12)),
            })

        return devices

    def _filter_mock_devices(
        self,
        limit: int = 100,
        offset: int = 0,
        registry_id: Optional[str] = None,
        device_prefix: Optional[str] = None,
        make: Optional[str] = None,
        model: Optional[str] = None,
        status: Optional[str] = None,
        search: Optional[str] = None,
    ) -> Dict[str, Any]:
        """Filters mock devices list according to query parameters."""
        filtered = self._mock_fleet
        if registry_id:
            filtered = [d for d in filtered if registry_id.lower() in d["registry_id"].lower()]
        if device_prefix:
            filtered = [d for d in filtered if d["device_id"].upper().startswith(device_prefix.upper())]
        if make:
            filtered = [d for d in filtered if make.lower() in d["make"].lower()]
        if model:
            filtered = [d for d in filtered if model.lower() in d["model"].lower()]
        if status:
            filtered = [d for d in filtered if d["liveness_status"].upper() == status.upper()]
        if search:
            s = search.lower()
            filtered = [
                d for d in filtered
                if s in d["device_id"].lower() or s in d["make"].lower() or s in d["model"].lower() or s in d["registry_id"].lower()
            ]

        total = len(filtered)
        paginated = filtered[offset : offset + limit]
        return {
            "total": total,
            "limit": limit,
            "offset": offset,
            "devices": paginated,
        }

    def _mock_portfolio_summary(self) -> Dict[str, Any]:
        fleet = self._mock_fleet
        online = len([d for d in fleet if d["liveness_status"] == "ONLINE"])
        offline = len([d for d in fleet if d["liveness_status"] == "OFFLINE"])
        error = len([d for d in fleet if d["liveness_status"] == "ERROR"])
        registries = len(set(d["registry_id"] for d in fleet))

        return {
            "device_counts": {
                "total": len(fleet),
                "online": online,
                "offline": offline,
                "error": error,
            },
            "registries_count": registries,
            "active_rollouts_count": len([r for r in self._mock_rollouts.values() if r.get("status") == "RUNNING"]),
            "critical_alerts_24h": 3,
        }

    def _mock_alerts(self, limit: int = 50, min_level: int = 500) -> List[Dict[str, Any]]:
        now = datetime.now(timezone.utc)
        return [
            {
                "id": 101,
                "registry_id": "ZZ-TRI-FECTA",
                "device_id": "AHU-4",
                "level": 800,
                "category": "system.hardware.comm_error",
                "message": "Supply fan VFD communication timeout on RS-485 bus",
                "detail": "BACnet MSTP timeout after 3 retries to controller 0x4A",
                "timestamp": (now - timedelta(minutes=14)).isoformat(),
            },
            {
                "id": 102,
                "registry_id": "ZZ-TRI-FECTA",
                "device_id": "VAV-108",
                "level": 600,
                "category": "pointset.sensor.out_of_range",
                "message": "Discharge air temperature exceeded high limit (28.4 C)",
                "detail": "Value above setpoint deadband 24.0 C for > 300s",
                "timestamp": (now - timedelta(hours=2, minutes=15)).isoformat(),
            },
            {
                "id": 103,
                "registry_id": "US-MTV-1",
                "device_id": "METER-PWR-3",
                "level": 500,
                "category": "system.liveness.heartbeat_missed",
                "message": "Device heartbeat missed interval (last seen > 15m)",
                "detail": "No UDP telemetry packet received on port 47808",
                "timestamp": (now - timedelta(hours=5)).isoformat(),
            },
        ][:limit]

    def _mock_device_detail(self, registry_id: str, device_id: str) -> Dict[str, Any]:
        match = next((d for d in self._mock_fleet if d["device_id"] == device_id), None)
        make = match["make"] if match else "Acme Controls"
        model = match["model"] if match else "HVAC-3000"
        serial = match["serial_no"] if match else f"SN-{device_id}-001"
        version = match["software_version"] if match else "2.4.1"
        now = datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")

        points_map = {
            "supply_air_temperature_sensor": {
                "value_state": "applied",
                "units": "Degrees-Celsius",
                "level": 300,
                "message": "Normal Operation (21.4 C)",
                "status_timestamp": now,
            },
            "return_air_temperature_sensor": {
                "value_state": "applied",
                "units": "Degrees-Celsius",
                "level": 300,
                "message": "Normal Operation (23.8 C)",
                "status_timestamp": now,
            },
            "supply_air_static_pressure_sensor": {
                "value_state": "applied",
                "units": "Pascals",
                "level": 300,
                "message": "Normal Operation (350 Pa)",
                "status_timestamp": now,
            },
            "fan_speed_command": {
                "value_state": "applied",
                "units": "Percent",
                "level": 300,
                "message": "Modulating (75%)",
                "status_timestamp": now,
            },
            "filter_alarm_status": {
                "value_state": "applied",
                "units": "Boolean",
                "level": 300,
                "message": "Filter Clean (False)",
                "status_timestamp": now,
            },
        }

        return {
            "registry_id": registry_id,
            "device_id": device_id,
            "metadata": {
                "make": make,
                "model": model,
                "serial_no": serial,
                "rev": "B.2",
                "sku": "SKU-HVAC-PRO",
                "room": "Mechanical Room 102",
                "floor": "Floor 1",
                "software": {"system": version, "hvac_app": "1.2.0"},
                "last_seen": now,
            },
            "state": {
                "system": {
                    "software": {"system": version, "hvac_app": "1.2.0"},
                    "operational": True,
                    "last_seen": now,
                },
                "pointset": {
                    "points": points_map,
                },
            },
            "config": {
                "system": {
                    "software": {"system": version, "hvac_app": "1.2.0"},
                },
                "pointset": {
                    "points": {
                        "fan_speed_command": {"setpoint": 75},
                    },
                },
            },
            "events": [
                {
                    "level": 300,
                    "category": "system.config.applied",
                    "message": "Configuration successfully synchronized",
                    "detail": None,
                    "timestamp": now,
                }
            ],
        }

    def _mock_device_etcd_properties(self, registry_id: str, device_id: str) -> Dict[str, Any]:
        """Generates realistic ETCD properties for a mock device."""
        detail = self._mock_device_detail(registry_id, device_id)
        is_gateway = "GAT" in device_id or "gateway" in device_id.lower() or device_id == "AHU-1"
        now = datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
        properties: Dict[str, str] = {
            ":attach": "GATEWAY" if is_gateway else "DIRECT",
            ":config": json.dumps(detail.get("config", {"system": {"software": {"system": "2.4.1"}}}), indent=2),
            ":last_config": now,
            ":last_state": json.dumps(detail.get("state", {"system": {"operational": True}})),
            ":last_state_time": now,
            ":metadata_str": json.dumps(detail.get("metadata", {"system": {"make": "Acme Controls"}})),
            ":num_id": "880803983",
            ":resource_type": "GATEWAY" if is_gateway else "DIRECT",
        }
        if is_gateway:
            properties["/c/bound_devices:AHU-2"] = ""
            properties["/c/bound_devices:AHU-22"] = ""
            properties["/c/bound_devices:SNS-4"] = ""

        sorted_props = dict(sorted(properties.items()))
        return {
            "registryId": registry_id,
            "deviceId": device_id,
            "properties": sorted_props,
        }

    def _mock_telemetry(self, registry_id: str, device_id: str, point_names: List[str]) -> Dict[str, Any]:
        now = datetime.now(timezone.utc)
        pts = point_names if point_names else ["supply_air_temperature_sensor", "fan_speed_command"]
        series_list = []

        for pt in pts:
            base_val = 21.5 if "temp" in pt else 75.0
            vals = []
            for m in range(15, -1, -1):
                t = (now - timedelta(minutes=m * 2)).isoformat()
                v = round(base_val + (m % 3) * 0.4 - 0.5, 2)
                vals.append({"time": t, "value": v, "field": "present_value_num"})
            series_list.append({"point_name": pt, "values": vals})

        return {
            "registry_id": registry_id,
            "device_id": device_id,
            "series": series_list,
        }

    def _generate_mock_messages(self) -> Dict[Tuple[str, str], List[Dict[str, Any]]]:
        """Generates initial mock message lifecycle records for AHU-22 and AHU-1."""
        now = datetime.now(timezone.utc)
        t_model = (now - timedelta(days=3)).strftime("%Y-%m-%dT%H:%M:%SZ")
        t_disc = (now - timedelta(minutes=30)).strftime("%Y-%m-%dT%H:%M:%SZ")
        t_prop = (now - timedelta(minutes=10)).strftime("%Y-%m-%dT%H:%M:%SZ")

        mock_msgs: Dict[Tuple[str, str], List[Dict[str, Any]]] = {}

        # AHU-22
        mock_msgs[("ZZ-TRI-FECTA", "AHU-22")] = [
            {
                "id": 1,
                "timestamp": t_model,
                "registry_id": "ZZ-TRI-FECTA",
                "device_id": "AHU-22",
                "sub_type": "model",
                "sub_folder": "system",
                "payload": {
                    "version": "1.5.7",
                    "timestamp": t_model,
                    "system": {
                        "location": {"site": "US-SFO-XYY", "room": "Room-204", "floor": "Floor-2"},
                        "serial_no": "SN-AHU-22",
                    },
                    "localnet": {
                        "families": {
                            "vendor": {"addr": "0x65"}
                        }
                    }
                },
                "updateFrom": None,
                "source": "registrar",
                "transaction_id": "TXN-init-01",
            },
            {
                "id": 2,
                "timestamp": t_disc,
                "registry_id": "ZZ-TRI-FECTA",
                "device_id": "GAT-123",
                "sub_type": "events",
                "sub_folder": "discovery",
                "payload": {
                    "timestamp": t_disc,
                    "generation": t_disc,
                    "family": "vendor",
                    "addr": "0x68",
                    "families": {
                        "vendor": {"addr": "0x68"},
                        "bacnet": {"addr": "10022"},
                        "ipv4": {"addr": "192.168.1.122"}
                    }
                },
                "updateFrom": None,
                "source": "pubber",
                "transaction_id": "TXN-scan-01",
            },
            {
                "id": 3,
                "timestamp": t_prop,
                "registry_id": "ZZ-TRI-FECTA",
                "device_id": "AHU-22",
                "sub_type": "propose",
                "sub_folder": "localnet",
                "payload": {
                    "version": "1.5.7",
                    "timestamp": t_prop,
                    "families": {
                        "vendor": {"addr": "0x68"},
                        "bacnet": {"addr": "10022"},
                        "ipv4": {"addr": "192.168.1.122"}
                    }
                },
                "updateFrom": t_model,
                "source": "butler",
                "transaction_id": "TXN-map-01",
            }
        ]

        return mock_msgs

    def _mock_device_messages(self, registry_id: str, device_id: str) -> List[Dict[str, Any]]:
        key = (registry_id, device_id)
        if key in self._mock_messages:
            return self._mock_messages[key]

        now = datetime.now(timezone.utc)
        t_model = (now - timedelta(days=2)).strftime("%Y-%m-%dT%H:%M:%SZ")
        return [
            {
                "id": 1,
                "timestamp": t_model,
                "registry_id": registry_id,
                "device_id": device_id,
                "sub_type": "model",
                "sub_folder": "system",
                "payload": {
                    "version": "1.5.7",
                    "timestamp": t_model,
                    "system": {"serial_no": f"SN-{device_id}-001"},
                    "localnet": {"families": {"bacnet": {"addr": "1001"}}}
                },
                "updateFrom": None,
                "source": "registrar",
                "transaction_id": "TXN-base",
            }
        ]
