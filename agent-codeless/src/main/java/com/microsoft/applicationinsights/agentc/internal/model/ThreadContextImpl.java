/*
 * ApplicationInsights-Java
 * Copyright (c) Microsoft Corporation
 * All rights reserved.
 *
 * MIT License
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this
 * software and associated documentation files (the ""Software""), to deal in the Software
 * without restriction, including without limitation the rights to use, copy, modify, merge,
 * publish, distribute, sublicense, and/or sell copies of the Software, and to permit
 * persons to whom the Software is furnished to do so, subject to the following conditions:
 * The above copyright notice and this permission notice shall be included in all copies or
 * substantial portions of the Software.
 * THE SOFTWARE IS PROVIDED *AS IS*, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED,
 * INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR
 * PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE
 * FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR
 * OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER
 * DEALINGS IN THE SOFTWARE.
 */

package com.microsoft.applicationinsights.agentc.internal.model;

import java.util.concurrent.TimeUnit;

import com.microsoft.applicationinsights.web.internal.RequestTelemetryContext;
import com.microsoft.applicationinsights.web.internal.correlation.TelemetryCorrelationUtilsCore;
import com.microsoft.applicationinsights.web.internal.correlation.TraceContextCorrelationCore;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.glowroot.instrumentation.engine.bytecode.api.ThreadContextPlus;
import org.glowroot.instrumentation.engine.impl.NopTransactionService;
import org.glowroot.instrumentation.api.AsyncQuerySpan;
import org.glowroot.instrumentation.api.AsyncSpan;
import org.glowroot.instrumentation.api.AuxThreadContext;
import org.glowroot.instrumentation.api.Getter;
import org.glowroot.instrumentation.api.MessageSupplier;
import org.glowroot.instrumentation.api.QueryMessageSupplier;
import org.glowroot.instrumentation.api.QuerySpan;
import org.glowroot.instrumentation.api.Setter;
import org.glowroot.instrumentation.api.Span;
import org.glowroot.instrumentation.api.Timer;
import org.glowroot.instrumentation.api.TimerName;

import static com.google.common.base.Preconditions.checkNotNull;

public class ThreadContextImpl implements ThreadContextPlus {

    private final IncomingSpanImpl incomingSpan;

    private final @Nullable RequestTelemetryContext telemetryContext;

    private int currentNestingGroupId;
    private int currentSuppressionKeyId;

    private final @Nullable TwoPartCompletion auxThreadAsyncCompletion;

    public ThreadContextImpl(IncomingSpanImpl incomingSpan, @Nullable RequestTelemetryContext telemetryContext,
                             int rootNestingGroupId, int rootSuppressionKeyId, boolean auxThread) {
        this.incomingSpan = incomingSpan;
        this.telemetryContext = telemetryContext;
        currentNestingGroupId = rootNestingGroupId;
        currentSuppressionKeyId = rootSuppressionKeyId;
        auxThreadAsyncCompletion = auxThread ? new TwoPartCompletion() : null;
    }

    @Override
    public boolean isInTransaction() {
        return true;
    }

    @Override
    public <C> Span startIncomingSpan(String transactionType, String transactionName, Getter<C> getter, C carrier,
                                      MessageSupplier messageSupplier, TimerName timerName,
                                      AlreadyInTransactionBehavior alreadyInTransactionBehavior) {
        // ApplicationInsights doesn't currently support local spans
        return NopTransactionService.LOCAL_SPAN;
    }

    @Override
    public Span startLocalSpan(MessageSupplier messageSupplier, TimerName timerName) {
        return NopTransactionService.LOCAL_SPAN;
    }

    @Override
    public QuerySpan startQuerySpan(String type, String dest, String text, QueryMessageSupplier queryMessageSupplier,
                                    TimerName timerName) {
        return new QuerySpanImpl(type, dest, text, queryMessageSupplier, System.currentTimeMillis());
    }

    @Override
    public QuerySpan startQuerySpan(String type, String dest, String text, long queryExecutionCount,
                                    QueryMessageSupplier queryMessageSupplier, TimerName timerName) {
        return new QuerySpanImpl(type, dest, text, queryMessageSupplier, System.currentTimeMillis());
    }

    @Override
    public AsyncQuerySpan startAsyncQuerySpan(String type, String dest, String text,
                                              QueryMessageSupplier queryMessageSupplier, TimerName timerName) {
        return new AsyncQuerySpanImpl(type, dest, text, queryMessageSupplier, System.currentTimeMillis());
    }

    @Override
    public <C> Span startOutgoingSpan(String type, String text, Setter<C> setter, C carrier,
                                      MessageSupplier messageSupplier, TimerName timerName) {
        // TODO revisit the point of text
        String outgoingSpanId = propagate(setter, carrier);
        return new OutgoingSpanImpl(type, text, System.currentTimeMillis(), outgoingSpanId, messageSupplier);
    }

    @Override
    public <C> AsyncSpan startAsyncOutgoingSpan(String type, String text, Setter<C> setter, C carrier,
                                                MessageSupplier messageSupplier, TimerName timerName) {
        // TODO revisit the point of text
        String outgoingSpanId = propagate(setter, carrier);
        return new AsyncOutgoingSpanImpl(type, text, System.currentTimeMillis(), outgoingSpanId, messageSupplier);
    }

    @Override
    public void captureLoggerSpan(MessageSupplier messageSupplier, @Nullable Throwable throwable) {
        LoggerSpans.track(messageSupplier, throwable, System.currentTimeMillis());
    }

    @Override
    public Timer startTimer(TimerName timerName) {
        // timers are not used by ApplicationInsights
        return NopTransactionService.TIMER;
    }

    @Override
    public AuxThreadContext createAuxThreadContext() {
        return new AuxThreadContextImpl(incomingSpan, telemetryContext);
    }

    @Override
    public void setTransactionAsync() {
        incomingSpan.setAsync();
    }

    @Override
    public void setTransactionAsyncComplete() {
        if (auxThreadAsyncCompletion == null || auxThreadAsyncCompletion.setPart1()) {
            incomingSpan.setAsyncComplete();
        }
    }

    @Override
    public void setTransactionType(@Nullable String transactionType, int priority) {
        // the core instrumentation only use this method to set transaction type to "Synthetic"
        // (when the "X-Glowroot-Transaction-Type" header is set to "Synthetic")
    }

    @Override
    public void setTransactionName(@Nullable String transactionName, int priority) {
        // currently ignoring priority, which is ok since just using core instrumentation
        if (transactionName != null) {
            incomingSpan.setTransactionName(transactionName);
        }
    }

    @Override
    public void setTransactionUser(@Nullable String user, int priority) {
    }

    @Override
    public void addTransactionAttribute(String name, @Nullable String value) {
        // the core instrumentation doesn't call this
    }

    @Override
    public void setTransactionSlowThreshold(long threshold, TimeUnit unit, int priority) {
        // the core instrumentation only calls this to set slow threshold to zero for Startup spans
    }

    @Override
    public void setTransactionError(Throwable t) {
        incomingSpan.setException(t);
    }

    @Override
    public void setTransactionError(@Nullable String message) {
        // TODO revisit
    }

    @Override
    public void setTransactionError(@Nullable String message, @Nullable Throwable t) {
        incomingSpan.setException(t);
    }

    @Override
    public void trackResourceAcquired(Object resource, boolean withLocationStackTrace) {
        // not supported for now
    }

    @Override
    public void trackResourceReleased(Object resource) {
        // not supported for now
    }

    @Override
    public @Nullable ServletRequestInfo getServletRequestInfo() {
        return incomingSpan.getServletRequestInfo();
    }

    @Override
    public void setServletRequestInfo(ServletRequestInfo servletRequestInfo) {
        incomingSpan.setServletRequestInfo(servletRequestInfo);
    }

    @Override
    public int getCurrentNestingGroupId() {
        return currentNestingGroupId;
    }

    @Override
    public void setCurrentNestingGroupId(int nestingGroupId) {
        this.currentNestingGroupId = nestingGroupId;
    }

    @Override
    public int getCurrentSuppressionKeyId() {
        return currentSuppressionKeyId;
    }

    @Override
    public void setCurrentSuppressionKeyId(int suppressionKeyId) {
        this.currentSuppressionKeyId = suppressionKeyId;
    }

    public void endAuxThreadContext() {
        checkNotNull(auxThreadAsyncCompletion);
        if (auxThreadAsyncCompletion.setPart2()) {
            incomingSpan.setAsyncComplete();
        }
    }

    private static <C> String propagate(Setter<C> setter, C carrier) {
        if (Global.isOutboundW3CEnabled()) {
            String traceparent = TraceContextCorrelationCore.generateChildDependencyTraceparent();
            if (traceparent == null) {
                // this means an error occurred (and was logged) in above method, so just return a valid outgoingSpanId
                return TelemetryCorrelationUtilsCore.generateChildDependencyId();
            }
            String outgoingSpanId = TraceContextCorrelationCore.createChildIdFromTraceparentString(traceparent);
            String tracestate = TraceContextCorrelationCore.retriveTracestate();
            setter.put(carrier, "traceparent", traceparent);
            if (Global.isOutboundW3CBackCompatEnabled()) {
                setter.put(carrier, "Request-Id", outgoingSpanId);
            }
            if (tracestate != null) {
                setter.put(carrier, "tracestate", tracestate);
            }
            return outgoingSpanId;
        } else {
            String outgoingSpanId = TelemetryCorrelationUtilsCore.generateChildDependencyId();
            String correlationContext = TelemetryCorrelationUtilsCore.retrieveCorrelationContext();
            String appCorrelationId = TelemetryCorrelationUtilsCore.retrieveApplicationCorrelationId();
            setter.put(carrier, "Request-Id", outgoingSpanId);
            setter.put(carrier, "Correlation-Context", correlationContext);
            setter.put(carrier, "Request-Context", appCorrelationId);
            return outgoingSpanId;
        }
    }
}