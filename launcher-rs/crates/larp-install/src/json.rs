//! Small accessors for Mojang's loosely structured JSON.

use crate::InstallError;
use serde_json::Value;

pub fn str<'a>(v: &'a Value, key: &str) -> Option<&'a str> {
    v.get(key).and_then(Value::as_str)
}

pub fn obj<'a>(v: &'a Value, key: &str) -> Option<&'a Value> {
    v.get(key).filter(|o| o.is_object())
}

pub fn arr<'a>(v: &'a Value, key: &str) -> &'a [Value] {
    v.get(key).and_then(Value::as_array).map_or(&[], Vec::as_slice)
}

pub fn require<'a>(v: &'a Value, key: &str, what: &str) -> Result<&'a str, InstallError> {
    str(v, key).ok_or_else(|| InstallError::Metadata(format!("{what} has no \"{key}\"")))
}

pub fn parse(text: &str, what: &str) -> Result<Value, InstallError> {
    serde_json::from_str(text).map_err(|e| InstallError::Metadata(format!("{what} is not valid JSON: {e}")))
}
