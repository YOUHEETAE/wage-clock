package com.wageclock.wageclock.domain.settlement;

import java.util.List;

public record BulkSettlementContext (Long bulkSettlementId, List<BulkSettlementItemContext> bulkSettlementItemContexts){
}
