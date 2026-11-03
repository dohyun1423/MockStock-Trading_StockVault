package com.stock.mockstock.domain.stock.controller;

import com.stock.mockstock.domain.stock.dto.VolumeRankingResponse;
import com.stock.mockstock.domain.stock.service.VolumeRankingService;
import com.stock.mockstock.domain.stock.service.VolumeRankingUnavailableException;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class VolumeRankingControllerTest {
    @Test
    void exposesSnapshotWithNoStoreAndUnavailableAs503() throws Exception {
        VolumeRankingService service = mock(VolumeRankingService.class);
        var mvc = MockMvcBuilders.standaloneSetup(new VolumeRankingController(service)).build();
        when(service.getRanking()).thenReturn(new VolumeRankingResponse(List.of(), "2026-11-03T00:00:00Z", false, 60));
        mvc.perform(get("/api/stocks/rankings/volume")).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.refreshIntervalSeconds").value(60))
                .andExpect(jsonPath("$.stale").value(false));
        when(service.getRanking()).thenThrow(new VolumeRankingUnavailableException("순위 조회 지연"));
        mvc.perform(get("/api/stocks/rankings/volume")).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value("순위 조회 지연"));
    }
}
