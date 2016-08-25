/*
 * Copyright 2015 LINE Corporation
 *
 * LINE Corporation licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package com.linecorp.armeria.server.thrift;

import static java.util.Objects.requireNonNull;

import java.util.List;
import java.util.Set;

import org.apache.thrift.AsyncProcessFunction;
import org.apache.thrift.ProcessFunction;
import org.apache.thrift.TApplicationException;
import org.apache.thrift.TBase;
import org.apache.thrift.TException;
import org.apache.thrift.TFieldIdEnum;
import org.apache.thrift.async.AsyncMethodCallback;

import com.linecorp.armeria.common.RequestContext;
import com.linecorp.armeria.common.RequestContext.PushHandle;
import com.linecorp.armeria.common.thrift.ThriftCall;
import com.linecorp.armeria.common.thrift.ThriftReply;
import com.linecorp.armeria.internal.thrift.ThriftFunction;
import com.linecorp.armeria.internal.thrift.ThriftServiceMetadata;
import com.linecorp.armeria.server.Service;
import com.linecorp.armeria.server.ServiceRequestContext;

/**
 * A {@link Service} that handles a {@link ThriftCall}.
 *
 * @see THttpService
 */
public final class ThriftCallService implements Service<ThriftCall, ThriftReply> {

    /**
     * Creates a new instance with the specified service implementation.
     *
     * @param implementation an implementation of {@code *.Iface} or {@code *.AsyncIface} service interface
     *                       generated by the Apache Thrift compiler
     */
    public static ThriftCallService of(Object implementation) {
        return new ThriftCallService(implementation);
    }

    private final Object implementation;
    private final ThriftServiceMetadata metadata;

    private ThriftCallService(Object implementation) {
        this.implementation = requireNonNull(implementation, "implementation");
        metadata = new ThriftServiceMetadata(implementation);
    }

    ThriftServiceMetadata metadata() {
        return metadata;
    }

    /**
     * Returns the Thrift service object that implements {@code *.Iface} or {@code *.AsyncIface}.
     */
    public Object implementation() {
        return implementation;
    }

    /**
     * Returns the Thrift service interfaces ({@code *.Iface} or {@code *.AsyncIface}) the Thrift service
     * object implements.
     */
    public Set<Class<?>> interfaces() {
        return metadata.interfaces();
    }

    @Override
    public ThriftReply serve(ServiceRequestContext ctx, ThriftCall call) throws Exception {
        // Ensure that such a method exists.
        final ThriftFunction f = metadata.function(call.method());
        if (f == null) {
            return new ThriftReply(call.seqId(), new TApplicationException(
                    TApplicationException.UNKNOWN_METHOD, "unknown method: " + call.method()));
        }

        final ThriftReply reply = new ThriftReply(call.seqId());
        invoke(ctx, f, call.params(), reply);
        return reply;
    }

    private void invoke(
            ServiceRequestContext ctx,
            ThriftFunction func, List<Object> args, ThriftReply reply) {

        try {
            final TBase<TBase<?, ?>, TFieldIdEnum> tArgs = func.newArgs(args);
            if (func.isAsync()) {
                invokeAsynchronously(ctx, func, tArgs, reply);
            } else {
                invokeSynchronously(ctx, func, tArgs, reply);
            }
        } catch (Throwable t) {
            reply.completeExceptionally(t);
        }
    }

    private void invokeAsynchronously(
            ServiceRequestContext ctx,
            ThriftFunction func, TBase<TBase<?, ?>, TFieldIdEnum> args, ThriftReply reply) throws TException {

        final AsyncProcessFunction<Object, TBase<TBase<?, ?>, TFieldIdEnum>, Object> f = func.asyncFunc();
        f.start(implementation, args, new AsyncMethodCallback<Object>() {
            @Override
            public void onComplete(Object response) {
                if (func.isOneWay()) {
                    reply.complete(null);
                } else {
                    reply.complete(response);
                }
            }

            @Override
            public void onError(Exception e) {
                reply.completeExceptionally(e);
            }
        });
    }

    private void invokeSynchronously(
            ServiceRequestContext ctx,
            ThriftFunction func, TBase<TBase<?, ?>, TFieldIdEnum> args, ThriftReply reply) {

        final ProcessFunction<Object, TBase<TBase<?, ?>, TFieldIdEnum>> f = func.syncFunc();
        ctx.blockingTaskExecutor().execute(() -> {
            if (reply.isDone()) {
                // Closed already most likely due to timeout.
                return;
            }

            try (PushHandle ignored = RequestContext.push(ctx)) {
                @SuppressWarnings("unchecked")
                TBase<TBase<?, ?>, TFieldIdEnum> result = f.getResult(implementation, args);
                if (func.isOneWay()) {
                    reply.complete(null);
                } else {
                    reply.complete(func.getResult(result));
                }
            } catch (Throwable t) {
                reply.completeExceptionally(t);
            }
        });
    }
}
