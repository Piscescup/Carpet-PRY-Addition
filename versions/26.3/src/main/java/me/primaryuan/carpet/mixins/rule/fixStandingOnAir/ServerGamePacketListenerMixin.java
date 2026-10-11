package me.primaryuan.carpet.mixins.rule.fixStandingOnAir;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import me.primaryuan.carpet.CarpetPrimaryuanSettings;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * fixStandingOnAir 规则 —— 回移植 26.4 Snapshot 1 对 MC-311474 / MC-311727 的官方修复，
 * 消除 26.3 "Player &lt;x&gt; standing on air - force-sending blocks below" 误判刷屏与下方方块错发。
 *
 * <p>根因（26.3 官方 server jar 反编译 + javap 双口径核实，★★★）：26.3 snapshot 10
 * 新增的幽灵方块重发机制中，"站空"判定与恢复调用在 {@code handlePlayerPositionChange}
 * 里位于 {@code this.player.move(...)} 之前：</p>
 * <pre>
 *   518: getfield verticalCollisionBelow → istore 39        // 守卫值预读
 *   523-530: iload 9 / ifeq 537 / iload 39 / ifne 537       // isOnGround && !playerStandsOnSomething
 *   534: invokevirtual forceSendPlayerSupportBlocks()V      // ← move 之前，碰撞箱还是旧位置
 *   537-554: new Vec3(xDist, yDist, zDist)
 *   557: invokevirtual ServerPlayer.move(MoverType, Vec3)V
 * </pre>
 * <p>{@code forceSendPlayerSupportBlocks} 在调用时读 {@code player.getBoundingBox()}
 * 下探四角（box.minY - 1.0E-5）发方块包，入口有 {@code tickCount >= lastSupportedBlocksSend + 200}
 * 冷却门。玩家跳上方块时旧碰撞箱四角下方无方块 → 误判站空 → 发错方块 + 写冷却 +
 * 每 10 秒刷一条日志（MC-311474 节奏吻合），且冷却被误耗后 200 tick 内真正需要的
 * 补发被压制（MC-311727）。</p>
 *
 * <p>官方修复（26.4-snapshot-1 jar sha1 1c5ef8ce… 反编译，全类仅此一处相关改动）：
 * {@code player.move} 提到检测块之前（字节码 557→543），恢复调用随之落到 move 后
 * （534→557）；守卫值读取（518）、五条 return 路径（92/224/436/843/1193）、
 * {@code forceSendPlayerSupportBlocks} 方法体与冷却字段读写逐字节不变。守卫分支
 * 虽移至 move 后求值，但读的仍是预读局部变量与不可变参数——分支决策值与 26.3 相同。</p>
 *
 * <p>修法：把 26.3 偏移 534 的恢复调用延迟到 557 的 move 之后执行，与官方 557 位置
 * 逐指令等价。守卫分支（523-530）原样保留，mixin 不复刻任何判定逻辑——是否"待执行"
 * 由原版分支是否流入调用点决定；延迟执行经 @Invoker 调用原版方法本体，内部读到的
 * 碰撞箱即 move 后新位置，原版恢复机制不取消。</p>
 *
 * <p>注入点（均限定 handlePlayerPositionChange，require=1 失配即启动报错）：</p>
 * <ul>
 *   <li>HEAD 复位 pending：标志只在单次调用内存活，其它 mod 对 move 的注入抛异常时
 *       不跨包泄漏；</li>
 *   <li>WrapOperation 接管 534 处调用点（规则关 = original.call 透传，原版行为等价，
 *       且与其它 mod 对同点的包装可组合）；</li>
 *   <li>INVOKE move + Shift.AFTER（557 后）补执行恢复调用。</li>
 * </ul>
 * <p>唯一性：forceSendPlayerSupportBlocks 的 INVOKE 在全类仅 534 一处，move 在方法内
 * 仅 557 一处，@At 无歧义（move 的 owner 是 ServerPlayer——其覆写过 move，@At target
 * 不能写 Entity）。handleAcceptTeleportPacket 路径恒传 isOnGround=false，原版守卫短路，
 * 不触及注入点。规则关闭时三个注入点全部短路透传，移动/恢复/冷却行为与原版一致。</p>
 *
 * <p>版本门禁：该机制 26.3 snapshot 10 引入，26.1.2 / 26.2 同类中
 * forceSendPlayerSupportBlocks / handlePlayerPositionChange / lastSupportedBlocksSend /
 * MIN_SUPPORTED_BLOCKS_RESEND_TICKS 均不存在（javap 核实），故本 mixin 仅登记于
 * versions/26.3 的 mixins.json 覆盖副本，其余版本不编译不加载。将来若新增 26.4+
 * 构建节点，本文件会随 26.3 覆盖沿预处理链级联，而官方已在该版本修复——须把 json
 * 条目从新节点的覆盖副本中剔除。</p>
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerGamePacketListenerMixin {

    /** 守卫已按旧位置命中，恢复调用待 move 应用后补执行 */
    @Unique
    private boolean fixStandingOnAir$pendingResend;

    @Invoker("forceSendPlayerSupportBlocks")
    public abstract void fixStandingOnAir$invokeForceSend();

    @Inject(method = "handlePlayerPositionChange", at = @At("HEAD"))
    private void fixStandingOnAir$resetPending(CallbackInfo ci) {
        this.fixStandingOnAir$pendingResend = false;
    }

    @WrapOperation(
            method = "handlePlayerPositionChange",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/network/ServerGamePacketListenerImpl;forceSendPlayerSupportBlocks()V"
            )
    )
    private void fixStandingOnAir$deferSend(ServerGamePacketListenerImpl instance, Operation<Void> original) {
        if (!CarpetPrimaryuanSettings.fixStandingOnAir) {
            original.call(instance);
            return;
        }
        this.fixStandingOnAir$pendingResend = true;
    }

    @Inject(
            method = "handlePlayerPositionChange",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ServerPlayer;move(Lnet/minecraft/world/entity/MoverType;Lnet/minecraft/world/phys/Vec3;)V",
                    shift = At.Shift.AFTER
            )
    )
    private void fixStandingOnAir$sendAfterMove(CallbackInfo ci) {
        if (!CarpetPrimaryuanSettings.fixStandingOnAir || !this.fixStandingOnAir$pendingResend) {
            return;
        }
        this.fixStandingOnAir$pendingResend = false;
        this.fixStandingOnAir$invokeForceSend();
    }
}
