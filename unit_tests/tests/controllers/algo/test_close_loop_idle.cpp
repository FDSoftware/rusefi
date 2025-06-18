/*
 * @file test_close_loop_idle.cpp
 *
 * @date: jun 18, 2025
 * @author FDSoftware
 */
#include "pch.h"
#include "closed_loop_idle.h"
using ICP = IIdleController::Phase;
using TgtInfo = IIdleController::TargetInfo;

class MockIdle : public MockIdleController {
  public:
    bool isIdling = true;
    bool useClosedLoop = true;
    ICP m_lastPhase = ICP::Idling;

    bool isIdlingOrTaper() const override {
      printf("isIdlingOrTaper\n");
        return isIdling;
    }
    ICP getCurrentPhase() const {
      printf("getCurrentPhase()\n");
        return m_lastPhase;
    }
    TgtInfo getTargetRpm(float clt){
        TgtInfo targetInfo;
        targetInfo.ClosedLoopTarget = 950;
        targetInfo.IdleEntryRpm = 900;
        targetInfo.IdleExitRpm = 1100;

        return targetInfo;
    }
};
/*
TEST(LongTermIdleTrim, getLtitFactor){
    EngineTestHelper eth(engine_type_e::TEST_ENGINE);
    engineConfiguration->ltitEnabled = true;
    engineConfiguration->ltitStableRpmThreshold = 50;
    engineConfiguration->idleMode = IM_AUTO;

    // Install mock idle controller
    MockIdle idler;
    engine->engineModules.get<IdleController>().set(&idler);

    auto factor = engine->m_ltit.getLtitFactor(0, 0);
    engineConfiguration->ltitStableTime = 1;
    // LTIT not initialized
    ASSERT_EQ(factor, 1.0f);

    engine->m_ltit.loadLtitFromConfig();
    engine->m_ltit.onIgnitionStateChanged(true);
    advanceTimeUs(MS2US(2000));

    engine->m_ltit.update(950,0,false,false,false,0.45);
    EXPECT_TRUE(engine->m_ltit.updatedLtit);
};
*/
TEST(LongTermIdleTrim, isValidConditionsForLearning){
    EngineTestHelper eth(engine_type_e::TEST_ENGINE);
    engineConfiguration->ltitEnabled = true;
    engineConfiguration->ltitStableRpmThreshold = 50;
    engineConfiguration->idleMode = IM_AUTO;
    engineConfiguration->ltitStableTime = 1;

    // Install mock idle controller
    MockIdle idler;
    engine->engineModules.get<IdleController>().set(&idler);
    idler.m_lastPhase = ICP::Idling;
    //LongTermIdleTrim m_ltit;
    auto factor = engine->m_ltit.getLtitFactor(0, 0);

    // LTIT not initialized
    EXPECT_FALSE(engine->m_ltit.isValidConditionsForLearning(0.45f));

    engine->m_ltit.loadLtitFromConfig();
    engine->m_ltit.onIgnitionStateChanged(true);
    advanceTimeUs(MS2US(2000));

    engine->m_ltit.update(950,0,false,false,false,0.45);
    EXPECT_TRUE(engine->m_ltit.isValidConditionsForLearning(0.45f));
};