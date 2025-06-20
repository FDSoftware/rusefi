/*
 * @file test_close_loop_idle.cpp
 *
 * @date: jun 18, 2025
 * @author FDSoftware
 */

#include "pch.h"
#include "closed_loop_idle.h"

using ::testing::StrictMock;
using ::testing::_;

using ICP = IIdleController::Phase;
using TgtInfo = IIdleController::TargetInfo;

class MockIdle : public MockIdleController {
public:
    bool useClosedLoop = true;

    ICP getCurrentPhase() const {
        return ICP::Idling;
    }
    TgtInfo getTargetRpm(float clt){
        TgtInfo targetInfo;
        targetInfo.ClosedLoopTarget = 950;
        targetInfo.IdleEntryRpm = 900;
        targetInfo.IdleExitRpm = 1100;

        return targetInfo;
    }
};

TEST(LongTermIdleTrim, isValidConditionsForLearning){
    EngineTestHelper eth(engine_type_e::TEST_ENGINE);
    // idle config
    engineConfiguration->idleMode = IM_AUTO;

    // ltit config
    engineConfiguration->ltitEnabled = true;
    engineConfiguration->ltitStableRpmThreshold = 50; // +-50 rpm
    engineConfiguration->ltitStableTime = 1; // second
    engineConfiguration->ltitIgnitionOnDelay = 1; // second
    engineConfiguration->ltitIntegratorThreshold = 4; // % ?

    constexpr int mocked_rpm = 920;

    StrictMock<MockIdle> idler;
    engine->engineModules.get<IdleController>().set(&idler);

    // LTIT not initialized
    EXPECT_FALSE(engine->m_ltit.isValidConditionsForLearning(4.5f));
    engine->m_ltit.loadLtitFromConfig();
    engine->m_ltit.onIgnitionStateChanged(true);

    advanceTimeUs(MS2US(500));
    // not enough time has passed yet to fulfill ltitIgnitionOnDelay
    EXPECT_FALSE(engine->m_ltit.isValidConditionsForLearning(4.5f));

    // integrator too low
    EXPECT_FALSE(engine->m_ltit.isValidConditionsForLearning(3.0f));

    // integrator too high
    EXPECT_FALSE(engine->m_ltit.isValidConditionsForLearning(-30.f));

    // isStableIdle update
    advanceTimeUs(MS2US(1000));
    engine->m_ltit.update(mocked_rpm,0,false,false,false,0.45);
    EXPECT_TRUE(engine->m_ltit.isValidConditionsForLearning(4.5f));
};

TEST(LongTermIdleTrim, onIgnitionStateChanged) {
    EngineTestHelper eth(engine_type_e::TEST_ENGINE);
    engine->m_ltit.isStableIdle = true;
    engine->m_ltit.onIgnitionStateChanged(true);
    EXPECT_FALSE(engine->m_ltit.isStableIdle);

    engine->m_ltit.updatedLtit = true;
    engine->m_ltit.onIgnitionStateChanged(false);
    EXPECT_TRUE(engine->m_ltit.m_pendingSave);
    EXPECT_FALSE(engine->m_ltit.updatedLtit);
}


TEST(LongTermIdleTrim, checkIfShouldSave) {
    EngineTestHelper eth(engine_type_e::TEST_ENGINE);
    engineConfiguration->ltitIgnitionOffSaveDelay = 2;

    // update m_pendingSave
    engine->m_ltit.updatedLtit = true;
    engine->m_ltit.onIgnitionStateChanged(false);

    // not enough time has passed yet to fulfill ltitIgnitionOffSaveDelay
    engine->m_ltit.checkIfShouldSave();
    EXPECT_TRUE(engine->m_ltit.m_pendingSave);

    advanceTimeUs(MS2US(2500));

    // now we can save the table
    engine->m_ltit.checkIfShouldSave();
    EXPECT_FALSE(engine->m_ltit.m_pendingSave);
}

TEST(LongTermIdleTrim, hasValidData) {
    EngineTestHelper eth(engine_type_e::TEST_ENGINE);

    // controller not initialized
    EXPECT_FALSE(engine->m_ltit.hasValidData());

    setArrayValues(config->ltitTable, 100);

    // correct data
    EXPECT_TRUE(engine->m_ltit.hasValidData());

    // Now we break almost the entire table with invalid values
    for (int i = 0; i < LTIT_TABLE_SIZE - 2 ; i++) {
        config->ltitTable[i] = 200;
    }
    EXPECT_FALSE(engine->m_ltit.hasValidData());
}

TEST(LongTermIdleTrim, update) {
    EngineTestHelper eth(engine_type_e::TEST_ENGINE);
    // NOTE: this code segment for configuring % startup LTIT is the same for isValidConditionsForLearning
    // idle config
    engineConfiguration->idleMode = IM_AUTO;

    // ltit config
    engineConfiguration->ltitEnabled = true;
    engineConfiguration->ltitStableRpmThreshold = 50; // +-50 rpm
    engineConfiguration->ltitStableTime = 1; // second
    engineConfiguration->ltitIgnitionOnDelay = 1; // second
    engineConfiguration->ltitIntegratorThreshold = 4; // % ?

    constexpr int mocked_rpm = 920;

    StrictMock<MockIdle> idler;
    engine->engineModules.get<IdleController>().set(&idler);

    // LTIT not initialized
    EXPECT_FALSE(engine->m_ltit.isValidConditionsForLearning(4.5f));
    engine->m_ltit.loadLtitFromConfig();
    engine->m_ltit.onIgnitionStateChanged(true);

    advanceTimeUs(MS2US(500));
    // ---------------------------------


}