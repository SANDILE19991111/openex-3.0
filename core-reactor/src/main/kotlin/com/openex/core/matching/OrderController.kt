package com.openex.core.matching

import com.openex.core.websocket.OrderBookBroadcaster
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Positive
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.math.BigDecimal
import java.util.UUID
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Column

@Entity
@Table(name = "order_idempotency_keys")
class OrderIdempotencyKey(
    @Id
    @Column(name = "idempotency_key", nullable = false, updatable = false)
    val idempotencyKey: String,

    @Column(name = "order_id", nullable = false)
    val orderId: UUID
)

interface OrderIdempotencyKeyRepository : JpaRepository<OrderIdempotencyKey, String>

data class CreateOrderRequest(
    @field:NotNull val userId: UUID,
    @field:NotBlank val tradingPair: String,
    @field:NotNull val side: OrderSide,
    @field:NotNull val orderType: OrderType,
    val price: BigDecimal? = null,
    val stopPrice: BigDecimal? = null,
    @field:NotNull @field:Positive val quantity: BigDecimal
)

data class OrderResponse(
    val id: UUID,
    val userId: UUID,
    val tradingPair: String,
    val side: OrderSide,
    val orderType: OrderType,
    val price: BigDecimal?,
    val stopPrice: BigDecimal?,
    val quantity: BigDecimal,
    val filledQuantity: BigDecimal,
    val status: OrderStatus,
    val tradesExecuted: Int
)

@RestController
@RequestMapping("/api/orders")
class OrderController(
    private val matchingEngine: MatchingEngine,
    private val orderRepository: OrderRepository,
    private val orderIdempotencyKeyRepository: OrderIdempotencyKeyRepository,
    private val orderBookBroadcaster: OrderBookBroadcaster,
    private val tradeSettlementService: TradeSettlementService,
    private val orderBalanceValidator: OrderBalanceValidator
) {

    @PostMapping
    fun createOrder(
        @RequestHeader("Idempotency-Key") idempotencyKey: String,
        @RequestBody req: CreateOrderRequest
    ): ResponseEntity<Any> {
        val existingKey = orderIdempotencyKeyRepository.findById(idempotencyKey)
        if (existingKey.isPresent) {
            val order = orderRepository.findById(existingKey.get().orderId).orElse(null)
            if (order != null) {
                return ResponseEntity.status(HttpStatus.OK).body(toResponse(order, tradesExecuted = 0))
            }
        }

        if (req.orderType == OrderType.LIMIT && req.price == null) {
            return ResponseEntity.badRequest().build()
        }
        if (req.orderType == OrderType.STOP && req.stopPrice == null) {
            return ResponseEntity.badRequest().build()
        }

        try {
            orderBalanceValidator.validate(
                userId = req.userId,
                tradingPair = req.tradingPair,
                side = req.side,
                orderType = req.orderType,
                price = req.price,
                quantity = req.quantity
            )
        } catch (ex: InsufficientBalanceException) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(mapOf("error" to ex.message))
        }

        val order = Order(
            userId = req.userId,
            tradingPair = req.tradingPair,
            side = req.side,
            orderType = req.orderType,
            price = req.price,
            stopPrice = req.stopPrice,
            quantity = req.quantity
        )

        val result = matchingEngine.submit(order)

        orderIdempotencyKeyRepository.save(OrderIdempotencyKey(idempotencyKey, result.order.id))

        // Move real money between the buyer's and seller's wallets for
        // every trade this submission generated. Each trade in result.trades
        // is fresh from this exact match — no risk of double-settling a
        // trade that was already settled on an earlier submission.
        result.trades.forEach { tradeSettlementService.settle(it) }

        // Push the updated order book + any trades (including any cascaded
        // stop-order fills) to every subscribed client in real time.
        orderBookBroadcaster.broadcast(matchingEngine.snapshotFor(req.tradingPair))
        orderBookBroadcaster.broadcastTrades(req.tradingPair, result.trades)

        return ResponseEntity.status(HttpStatus.CREATED)
            .body(toResponse(result.order, result.trades.size))
    }

    @GetMapping("/{id}")
    fun getOrder(@PathVariable id: UUID): ResponseEntity<OrderResponse> {
        val order = orderRepository.findById(id).orElse(null) ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(toResponse(order, tradesExecuted = 0))
    }

    /**
     * Lists a user's orders. Pass `tradingPair` to scope to one pair (used
     * by the chart to draw the user's own resting bid/ask/stop lines).
     * Omit it to get the user's full order history across every pair
     * they've traded — newest first — for a proper order history view.
     */
    @GetMapping
    fun listOrders(
        @RequestParam userId: UUID,
        @RequestParam(required = false) tradingPair: String?
    ): ResponseEntity<List<OrderResponse>> {
        val orders = if (tradingPair != null) {
            orderRepository.findAllByUserIdAndTradingPair(userId, tradingPair)
        } else {
            orderRepository.findAllByUserIdOrderByCreatedAtDesc(userId)
        }
        return ResponseEntity.ok(orders.map { toResponse(it, tradesExecuted = 0) })
    }

    @DeleteMapping("/{id}")
    fun cancelOrder(@PathVariable id: UUID): ResponseEntity<OrderResponse> {
        val order = matchingEngine.cancel(id) ?: return ResponseEntity.notFound().build()
        orderBookBroadcaster.broadcast(matchingEngine.snapshotFor(order.tradingPair))
        return ResponseEntity.ok(toResponse(order, tradesExecuted = 0))
    }

    @GetMapping("/book/{tradingPair}")
    fun getOrderBook(@PathVariable tradingPair: String): ResponseEntity<OrderBookSnapshot> =
        ResponseEntity.ok(matchingEngine.snapshotFor(tradingPair))

    private fun toResponse(order: Order, tradesExecuted: Int) = OrderResponse(
        id = order.id,
        userId = order.userId,
        tradingPair = order.tradingPair,
        side = order.side,
        orderType = order.orderType,
        price = order.price,
        stopPrice = order.stopPrice,
        quantity = order.quantity,
        filledQuantity = order.filledQuantity,
        status = order.status,
        tradesExecuted = tradesExecuted
    )
}
