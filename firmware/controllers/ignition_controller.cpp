#include "pch.h"

bool isIgnVoltage() {
  return Sensor::getOrZero(SensorType::BatteryVoltage) > 5;
}

void IgnitionController::onSlowCallback() {
	// default to 0 if failed sensor to prevent accidental ign-on if battery
	// input misconfigured (or the ADC hasn't started yet)
	auto hasIgnVoltage = isIgnVoltage();

	if (hasIgnVoltage) {
		m_timeSinceIgnVoltage.reset();
	}

	if (hasIgnVoltage == m_lastState) {
		// nothing to do, states match
		return;
	}

	// Ignore low voltage transients - we may see this at the start of cranking
	// and we don't want to
	if (!hasIgnVoltage && secondsSinceIgnVoltage() < 0.2f) {
		return;
	}

	// Store state and notify other modules of the change
	m_lastState = hasIgnVoltage;
	engine->engineModules.apply_all([&](auto& m) { m.onIgnitionStateChanged(hasIgnVoltage); });
}
