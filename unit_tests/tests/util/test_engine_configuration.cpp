//
// Created by kifir on 11/4/24.
//

#include "pch.h"

#include "test_engine_configuration.h"

#include "engine_configuration_defaults.h"

TestEngineConfiguration& TestEngineConfiguration::getInstance() {
    return instance;
}

void TestEngineConfiguration::configureLaunchControlEnabled(const std::optional<bool> launchControlEnabled) {
    if (launchControlEnabled.has_value()) {
        engineConfiguration->launchControlEnabled = launchControlEnabled.value();
    } else {
        ASSERT_FALSE(engineConfiguration->launchControlEnabled); // check default value
    }
}

void TestEngineConfiguration::configureLaunchActivationMode(
    const std::optional<launchActivationMode_e> launchActivationMode
) {
    if (launchActivationMode.has_value()) {
        engineConfiguration->launchActivationMode = launchActivationMode.value();
    } else {
        ASSERT_EQ(
            engineConfiguration->launchActivationMode,
            engine_configuration_defaults::LAUNCH_ACTIVATION_MODE
        ); // check default value
    }
}

void TestEngineConfiguration::configureLaunchSpeedThreshold(const std::optional<int> launchSpeedThreshold) {
    if (launchSpeedThreshold.has_value()) {
        engineConfiguration->launchSpeedThreshold = launchSpeedThreshold.value();
    } else {
        ASSERT_EQ(
            engineConfiguration->launchSpeedThreshold,
            engine_configuration_defaults::LAUNCH_SPEED_THRESHOLD
        ); // check default value
    }
}

void TestEngineConfiguration::configureLaunchRpm(const std::optional<int> launchRpm) {
    if (launchRpm.has_value()) {
        engineConfiguration->launchRpm = launchRpm.value();
    } else {
        ASSERT_EQ(engineConfiguration->launchRpm, 0); // check default value
    }
}

void TestEngineConfiguration::configureLaunchRpmWindow(const std::optional<int> launchRpmWindow) {
    if (launchRpmWindow.has_value()) {
        engineConfiguration->launchRpmWindow = launchRpmWindow.value();
    } else {
        ASSERT_EQ(engineConfiguration->launchRpmWindow, 0); // check default value
    }
}

void TestEngineConfiguration::configureLaunchCorrectionsEndRpm(const std::optional<int> launchCorrectionsEndRpm) {
    if (launchCorrectionsEndRpm.has_value()) {
        engineConfiguration->launchCorrectionsEndRpm = launchCorrectionsEndRpm.value();
    } else {
        ASSERT_EQ(engineConfiguration->launchCorrectionsEndRpm, 0); // check default value
    }
}

void TestEngineConfiguration::configureIgnitionRetardEnable(std::optional<bool> ignitionRetardEnable) {
    if (ignitionRetardEnable.has_value()) {
        engineConfiguration->enableLaunchRetard = ignitionRetardEnable.value();
    } else {
        ASSERT_FALSE(engineConfiguration->enableLaunchRetard); // check default value
    }
}

void TestEngineConfiguration::configureIgnitionRetard(std::optional<float> ignitionRetard) {
    if (ignitionRetard.has_value()) {
        engineConfiguration->launchTimingRetard = ignitionRetard.value();
    } else {
        ASSERT_EQ(engineConfiguration->launchTimingRetard, 0); // check default value
    }
}

void TestEngineConfiguration::configureSmoothRetardMode(std::optional<bool> smoothRetardMode) {
    if (smoothRetardMode.has_value()) {
        engineConfiguration->launchSmoothRetard = smoothRetardMode.value();
    } else {
        ASSERT_FALSE(engineConfiguration->launchSmoothRetard); // check default value
    }
}

void TestEngineConfiguration::configureEnableIgnitionCut(const std::optional<bool> enableIgnitionCut) {
    if (enableIgnitionCut.has_value()) {
        engineConfiguration->launchSparkCutEnable = enableIgnitionCut.value();
    } else {
        ASSERT_FALSE(engineConfiguration->launchSparkCutEnable); // check default value
    }
}

void TestEngineConfiguration::configureInitialIgnitionCutPercent(const std::optional<int> initialIgnitionCutPercent) {
    if (initialIgnitionCutPercent.has_value()) {
        engineConfiguration->initialIgnitionCutPercent = initialIgnitionCutPercent.value();
    } else {
        ASSERT_EQ(engineConfiguration->initialIgnitionCutPercent, 0); // check default value
    }
}

void TestEngineConfiguration::configureFinalIgnitionCutPercentBeforeLaunch(
    const std::optional<int> finalIgnitionCutPercentBeforeLaunch
) {
    if (finalIgnitionCutPercentBeforeLaunch.has_value()) {
        engineConfiguration->finalIgnitionCutPercentBeforeLaunch = finalIgnitionCutPercentBeforeLaunch.value();
    } else {
        ASSERT_EQ(engineConfiguration->finalIgnitionCutPercentBeforeLaunch, 0); // check default value
    }
}

void TestEngineConfiguration::configureTorqueReductionEnabled(const std::optional<bool> torqueReductionEnabled) {
    if (torqueReductionEnabled.has_value()) {
        engineConfiguration->torqueReductionEnabled = torqueReductionEnabled.value();
    } else {
        ASSERT_EQ(
            engineConfiguration->torqueReductionEnabled,
            engine_configuration_defaults::ENABLE_SHIFT_TORQUE_REDUCTION
        ); // check default value
    }
}

void TestEngineConfiguration::configureTorqueReductionActivationMode(
    const std::optional<torqueReductionActivationMode_e> activationMode
) {
    if (activationMode.has_value()) {
        engineConfiguration->torqueReductionActivationMode = activationMode.value();
    } else {
        ASSERT_EQ(
            engineConfiguration->torqueReductionActivationMode,
            engine_configuration_defaults::TORQUE_REDUCTION_ACTIVATION_MODE
        ); // check default value
    }
}

void TestEngineConfiguration::configureTorqueReductionButton(const std::optional<switch_input_pin_e> pin) {
    if (pin.has_value()) {
        engineConfiguration->torqueReductionTriggerPin = pin.value();
    } else {
        ASSERT_EQ(
            engineConfiguration->torqueReductionTriggerPin,
            engine_configuration_defaults::TORQUE_REDUCTION_TRIGGER_PIN
        ); // check default value
    }
}

void TestEngineConfiguration::configureTorqueReductionButtonInverted(const std::optional<bool> pinInverted) {
    if (pinInverted.has_value()) {
        engineConfiguration->torqueReductionTriggerPinInverted = pinInverted.value();
    } else {
        ASSERT_EQ(
            engineConfiguration->torqueReductionTriggerPinInverted,
            engine_configuration_defaults::TORQUE_REDUCTION_TRIGGER_PIN_INVERTED
        ); // check default value
    }
}

void TestEngineConfiguration::configureLaunchActivatePin(const std::optional<switch_input_pin_e> pin) {
    if (pin.has_value()) {
        engineConfiguration->launchActivatePin = pin.value();
    } else {
        ASSERT_EQ(
            engineConfiguration->launchActivatePin,
            engine_configuration_defaults::LAUNCH_ACTIVATE_PIN
        ); // check default value
    }
}

void TestEngineConfiguration::configureLaunchActivateInverted(const std::optional<bool> pinInverted) {
    if (pinInverted.has_value()) {
        engineConfiguration->launchActivateInverted = pinInverted.value();
    } else {
        ASSERT_EQ(
            engineConfiguration->launchActivateInverted,
            engine_configuration_defaults::LAUNCH_ACTIVATE_PIN_INVERTED
        ); // check default value
    }
}

void TestEngineConfiguration::configureLimitTorqueReductionTime(std::optional<bool> limitTorqueReductionTime) {
    if (limitTorqueReductionTime.has_value()) {
        engineConfiguration->limitTorqueReductionTime = limitTorqueReductionTime.value();
    } else {
        ASSERT_EQ(
            engineConfiguration->limitTorqueReductionTime,
            engine_configuration_defaults::LIMIT_TORQUE_REDUCTION_TIME
        ); // check default value
    }
}

void TestEngineConfiguration::configureTorqueReductionTime(std::optional<float> timeout) {
    if (timeout.has_value()) {
        engineConfiguration->torqueReductionTime = timeout.value();
    } else {
        ASSERT_EQ(
            engineConfiguration->torqueReductionTime,
            engine_configuration_defaults::TORQUE_REDUCTION_TIME
        ); // check default value
    }
}

void TestEngineConfiguration::configureTorqueReductionArmingRpm(const std::optional<float> armingRpm) {
    if (armingRpm.has_value()) {
        engineConfiguration->torqueReductionArmingRpm = armingRpm.value();
    } else {
        ASSERT_EQ(
            engineConfiguration->torqueReductionArmingRpm,
            engine_configuration_defaults::TORQUE_REDUCTION_ARMING_RPM
        ); // check default value
    }
}

void TestEngineConfiguration::configureTorqueReductionArmingApp(const std::optional<float> armingApp) {
    if (armingApp.has_value()) {
        engineConfiguration->torqueReductionArmingApp = armingApp.value();
    } else {
        ASSERT_EQ(
            engineConfiguration->torqueReductionArmingApp,
            engine_configuration_defaults::TORQUE_REDUCTION_ARMING_APP
        ); // check default value
    }
}

void TestEngineConfiguration::configureTorqueReductionIgnitionCut(const std::optional<int8_t> ignitionCut) {
    if (ignitionCut.has_value()) {
        engineConfiguration->torqueReductionIgnitionCut = ignitionCut.value();
    } else {
        ASSERT_EQ(
            engineConfiguration->torqueReductionIgnitionCut,
            engine_configuration_defaults::TORQUE_REDUCTION_IGNITION_CUT
        ); // check default value
    }
}

void TestEngineConfiguration::configureTorqueReductionIgnitionRetard(const std::optional<int8_t> ignitionRetard) {
    if (ignitionRetard.has_value()) {
        engineConfiguration->torqueReductionIgnitionRetard = ignitionRetard.value();
    } else {
        ASSERT_EQ(
            engineConfiguration->torqueReductionIgnitionRetard,
            engine_configuration_defaults::TORQUE_REDUCTION_IGNITION_RETARD
        ); // check default value
    }
}

TestEngineConfiguration::TestEngineConfiguration() {
}

TestEngineConfiguration TestEngineConfiguration::instance;