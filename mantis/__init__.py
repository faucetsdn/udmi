"""Mantis: Autonomous UDMI Diagnostic Agent and Control Plane."""

from mantis.agent import MantisAgent
from mantis.config import CONFIG, MantisConfig
from mantis.skills import Skill, SkillManager

__all__ = [
    "MantisAgent",
    "SkillManager",
    "Skill",
    "CONFIG",
    "MantisConfig",
]
