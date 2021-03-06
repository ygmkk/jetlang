package org.jetlang.channels;

import org.jetlang.core.Callback;
import org.jetlang.core.Disposable;
import org.jetlang.fibers.Fiber;
import org.jetlang.fibers.PoolFiberFactory;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static junit.framework.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class MemoryRequestChannelTest {
    private static ExecutorService pool;
    private static PoolFiberFactory fiberPool;

    private List<Fiber> active = new ArrayList<Fiber>();

    @BeforeClass
    public static void createPool() {
        pool = Executors.newCachedThreadPool();
        fiberPool = new PoolFiberFactory(pool);
    }

    @AfterClass
    public static void destroyPool() {
        if (pool != null) {
            pool.shutdownNow();
        }
        if (fiberPool != null) {
            fiberPool.dispose();
        }
    }

    @After
    public void stopAll() {
        for (Fiber fiber : active) {
            fiber.dispose();
        }
        active.clear();
    }

    @Test
    public void simpleRequestResponse() throws InterruptedException {
        Fiber req = startFiber();
        Fiber reply = startFiber();
        MemoryRequestChannel<String, Integer> channel = new MemoryRequestChannel<String, Integer>();
        Callback<Request<String, Integer>> onReq = new Callback<Request<String, Integer>>() {
            public void onMessage(Request<String, Integer> message) {
                message.reply(1);
            }
        };
        channel.subscribe(reply, onReq);

        final CountDownLatch done = new CountDownLatch(1);
        Callback<Integer> onReply = new Callback<Integer>() {
            public void onMessage(Integer message) {
                assertEquals(1, message.intValue());
                done.countDown();
            }
        };
        AsyncRequest.withOneReply(req, channel, "hello", onReply);
        assertTrue(done.await(10, TimeUnit.SECONDS));
    }

    @Test
    public void simpleRequestResponseWithTimeout() throws InterruptedException {
        Fiber req = startFiber();
        MemoryRequestChannel<String, Integer> channel = new MemoryRequestChannel<String, Integer>();

        final CountDownLatch done = new CountDownLatch(1);
        Callback<Integer> onReply = new Callback<Integer>() {
            public void onMessage(Integer message) {
                fail();
            }
        };
        Runnable runnable = new Runnable() {
            public void run() {
                done.countDown();
            }
        };
        AsyncRequest.withOneReply(req, channel, "hello", onReply, 10, TimeUnit.MILLISECONDS, runnable);
        assertTrue(done.await(10, TimeUnit.SECONDS));
    }

    @Test
    public void requestForSingleReplyThatTimesOutShouldEndRequest() throws InterruptedException {
        Fiber req = startFiber();
        MemoryRequestChannel<String, Integer> channel = new MemoryRequestChannel<String, Integer>();

        Fiber reply = startFiber();
        Callback<Request<String, Integer>> onReq = new Callback<Request<String, Integer>>() {
            public void onMessage(Request<String, Integer> message) {
                //ignore requests
            }
        };
        final CountDownLatch endSession = new CountDownLatch(1);
        Callback<SessionClosed<String>> onReqEnd = new Callback<SessionClosed<String>>() {
            public void onMessage(SessionClosed<String> message) {
                assertEquals("hello", message.getOriginalRequest());
                endSession.countDown();
            }
        };
        channel.subscribe(reply, onReq, onReqEnd);

        final CountDownLatch done = new CountDownLatch(1);
        Callback<Integer> onReply = new Callback<Integer>() {
            public void onMessage(Integer message) {
                fail();
            }
        };
        Runnable runnable = new Runnable() {
            public void run() {
                done.countDown();
            }
        };
        AsyncRequest.withOneReply(req, channel, "hello", onReply, 10, TimeUnit.MILLISECONDS, runnable);
        assertTrue(done.await(10, TimeUnit.SECONDS));
        assertTrue(endSession.await(10, TimeUnit.SECONDS));
    }


    @Test
    public void simpleRequestResponseWithEndSession() throws InterruptedException {
        Fiber req = startFiber();
        Fiber reply = startFiber();
        MemoryRequestChannel<String, Integer> channel = new MemoryRequestChannel<String, Integer>();
        final CountDownLatch done = new CountDownLatch(1);
        Callback<Request<String, Integer>> onReq = new Callback<Request<String, Integer>>() {
            public void onMessage(Request<String, Integer> message) {
                message.reply(1);
            }
        };
        Callback<SessionClosed<String>> onEnd = new Callback<SessionClosed<String>>() {
            public void onMessage(SessionClosed<String> message) {
                done.countDown();
            }
        };
        channel.subscribe(reply, onReq, onEnd);

        final CountDownLatch rcv = new CountDownLatch(1);
        Callback<Integer> onReply = new Callback<Integer>() {
            public void onMessage(Integer message) {
                assertEquals(1, message.intValue());
                rcv.countDown();
            }
        };
        Disposable reqController = channel.publish(req, "hello", onReply);
        assertTrue(rcv.await(10, TimeUnit.SECONDS));
        reqController.dispose();
        assertTrue(done.await(10, TimeUnit.SECONDS));

    }

    @Test
    public void fiveMessagesRequestResponseWithEndSession() throws InterruptedException {
        Fiber req = startFiber();
        Fiber reply = startFiber();
        MemoryRequestChannel<String, Integer> channel = new MemoryRequestChannel<String, Integer>();
        final CountDownLatch done = new CountDownLatch(1);
        Callback<Request<String, Integer>> onReq = new Callback<Request<String, Integer>>() {
            public void onMessage(Request<String, Integer> message) {
                for (int i = 0; i < 10; i++) {
                    message.reply(i);
                }
            }
        };
        Callback<SessionClosed<String>> onEnd = new Callback<SessionClosed<String>>() {
            public void onMessage(SessionClosed<String> message) {
                done.countDown();
            }
        };
        channel.subscribe(reply, onReq, onEnd);

        final CountDownLatch rcv = new CountDownLatch(1);

        AsyncRequest<String, Integer> async = new AsyncRequest<String, Integer>(req);
        async.setResponseCount(5);
        Callback<List<Integer>> onReply = new Callback<List<Integer>>() {
            public void onMessage(List<Integer> message) {
                assertEquals(5, message.size());
                rcv.countDown();
            }
        };
        async.publish(channel, "hello", onReply);
        assertTrue(rcv.await(10, TimeUnit.SECONDS));
        assertTrue(done.await(10, TimeUnit.SECONDS));
    }


    @Test
    public void asyncRequestTimeout() throws InterruptedException {
        Fiber req = startFiber();
        MemoryRequestChannel<String, Integer> channel = new MemoryRequestChannel<String, Integer>();

        final CountDownLatch timeout = new CountDownLatch(1);
        Callback<List<Integer>> onTimeout = new Callback<List<Integer>>() {
            public void onMessage(List<Integer> message) {
                assertEquals(0, message.size());
                timeout.countDown();
            }
        };
        Callback<List<Integer>> onResp = new Callback<List<Integer>>() {
            public void onMessage(List<Integer> message) {
                fail();
            }
        };
        AsyncRequest<String, Integer> async = new AsyncRequest<String, Integer>(req);
        async.setTimeout(onTimeout, 10, TimeUnit.MILLISECONDS)
                .publish(channel, "hello", onResp);
        assertTrue(timeout.await(10, TimeUnit.SECONDS));
    }


    private Fiber startFiber() {
        Fiber f = fiberPool.create();
        active.add(f);
        f.start();
        return f;
    }
}
