"""Synthetic demo data for CareBridge: care units, demo users and discharge cases."""

from .generator import FIXTURE_VERSION, generate
from .check import check

__all__ = ["FIXTURE_VERSION", "generate", "check"]
