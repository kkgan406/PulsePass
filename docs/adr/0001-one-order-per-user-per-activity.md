# ADR-0001: One Active Order Per User Per Activity

状态：**Accepted**

## Context

活动可提供多个票种，第一版需要明确限购范围及有效订单的含义，同时控制业务复杂度。

## Decision

选择 User + Activity 范围内最多一笔有效订单。PENDING 和 CONFIRMED 有效，CANCELLED 不计入；因此该规则并不禁止同一用户和活动拥有多笔历史取消订单。用户购买某活动 VIP 后不能再买该活动的 Regular，但可以购买其他活动。

此规则简化业务模型，使项目聚焦高并发和一致性，也便于演示重复订单问题，避免过早扩大票务规则复杂度。

## Alternatives Considered

1. **User + Activity 唯一（选择）**：对有效订单按活动限购，而非对所有历史订单无条件唯一。
2. **User + TicketType 唯一**：允许同一用户购买同一活动的多个票种，扩大限购范围和业务复杂度。
3. **每个 TicketType 可购买 N 张**：需要定义数量上限及更多库存和限购规则，超出第一版需要。

## Consequences

规则跨票种生效，订单需关联 Activity；取消订单不再占用有效订单名额。并发检查与创建订单之间仍可能出现重复有效订单，后续需研究一致性保障。取消后的库存处理和状态转换另行确定。
