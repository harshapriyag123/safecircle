from pydantic import BaseModel, Field
from typing import Literal


PrivacyMode = Literal["STATUS_ONLY", "APPROXIMATE", "PRECISE_ON_ESCALATION"]


class SessionUpsert(BaseModel):
    id: str = Field(min_length=4, max_length=128)
    owner_id: str = Field(min_length=3, max_length=128)
    mode: str = Field(min_length=2, max_length=64)
    destination: str | None = Field(default=None, max_length=256)
    started_at: int
    expected_end_at: int
    last_check_in_at: int
    state: str = "NORMAL"
    battery_percent: int | None = Field(default=None, ge=0, le=100)
    latitude: float | None = Field(default=None, ge=-90, le=90)
    longitude: float | None = Field(default=None, ge=-180, le=180)
    location_accuracy: float | None = Field(default=None, ge=0)
    privacy_mode: PrivacyMode = "PRECISE_ON_ESCALATION"
    capsule: dict | None = None
    resolved: bool = False
    resolved_at: int | None = None


class SessionPatch(BaseModel):
    expected_end_at: int | None = None
    last_check_in_at: int | None = None
    state: str | None = None
    battery_percent: int | None = Field(default=None, ge=0, le=100)
    latitude: float | None = Field(default=None, ge=-90, le=90)
    longitude: float | None = Field(default=None, ge=-180, le=180)
    location_accuracy: float | None = Field(default=None, ge=0)
    privacy_mode: PrivacyMode | None = None
    capsule: dict | None = None
    resolved: bool | None = None
    resolved_at: int | None = None


class GuardianInviteCreate(BaseModel):
    session_id: str
    owner_id: str
    role: str = "guardian"
    ttl_minutes: int = Field(default=1440, ge=5, le=10080)


class ClientEvent(BaseModel):
    id: str
    session_id: str | None = None
    event_type: str
    payload: dict = Field(default_factory=dict)
    created_at: int


class ClientEventBatch(BaseModel):
    events: list[ClientEvent]


class RevenueCatWebhookEnvelope(BaseModel):
    event: dict
