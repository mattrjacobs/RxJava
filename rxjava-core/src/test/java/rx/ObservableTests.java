package rx;

import static org.junit.Assert.*;
import static org.mockito.Matchers.*;
import static org.mockito.Mockito.*;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import rx.observables.ConnectableObservable;
import rx.subscriptions.BooleanSubscription;
import rx.subscriptions.Subscriptions;
import rx.util.functions.Action1;
import rx.util.functions.Func1;
import rx.util.functions.Func2;

public class ObservableTests {

    @Mock
    Observer<Integer> w;

    @Before
    public void before() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testCreate() {

        Observable<String> observable = Observable.create(new Func1<Observer<String>, Subscription>() {

            @Override
            public Subscription call(Observer<String> Observer) {
                Observer.onNext("one");
                Observer.onNext("two");
                Observer.onNext("three");
                Observer.onCompleted();
                return Subscriptions.empty();
            }

        });

        @SuppressWarnings("unchecked")
        Observer<String> aObserver = mock(Observer.class);
        observable.subscribe(aObserver);
        verify(aObserver, times(1)).onNext("one");
        verify(aObserver, times(1)).onNext("two");
        verify(aObserver, times(1)).onNext("three");
        verify(aObserver, Mockito.never()).onError(any(Throwable.class));
        verify(aObserver, times(1)).onCompleted();
    }

    @Test
    public void testReduce() {
        Observable<Integer> observable = Observable.from(1, 2, 3, 4);
        observable.reduce(new Func2<Integer, Integer, Integer>() {

            @Override
            public Integer call(Integer t1, Integer t2) {
                return t1 + t2;
            }

        }).subscribe(w);
        // we should be called only once
        verify(w, times(1)).onNext(anyInt());
        verify(w).onNext(10);
    }

    @Test
    public void testReduceWithInitialValue() {
        Observable<Integer> observable = Observable.from(1, 2, 3, 4);
        observable.reduce(50, new Func2<Integer, Integer, Integer>() {

            @Override
            public Integer call(Integer t1, Integer t2) {
                return t1 + t2;
            }

        }).subscribe(w);
        // we should be called only once
        verify(w, times(1)).onNext(anyInt());
        verify(w).onNext(60);
    }

    @Test
    public void testSequenceEqual() {
        Observable<Integer> first = Observable.from(1, 2, 3);
        Observable<Integer> second = Observable.from(1, 2, 4);
        @SuppressWarnings("unchecked")
        Observer<Boolean> result = mock(Observer.class);
        Observable.sequenceEqual(first, second).subscribe(result);
        verify(result, times(2)).onNext(true);
        verify(result, times(1)).onNext(false);
    }

    @Test
    public void testOnSubscribeFails() {
        @SuppressWarnings("unchecked")
        Observer<String> observer = mock(Observer.class);
        final RuntimeException re = new RuntimeException("bad impl");
        Observable<String> o = Observable.create(new Func1<Observer<String>, Subscription>() {

            @Override
            public Subscription call(Observer<String> t1) {
                throw re;
            }

        });
        o.subscribe(observer);
        verify(observer, times(0)).onNext(anyString());
        verify(observer, times(0)).onCompleted();
        verify(observer, times(1)).onError(re);
    }

    @Test
    public void testMaterializeDematerializeChaining() {
        Observable<Integer> obs = Observable.just(1);
        Observable<Integer> chained = obs.materialize().dematerialize();

        @SuppressWarnings("unchecked")
        Observer<Integer> observer = mock(Observer.class);
        chained.subscribe(observer);

        verify(observer, times(1)).onNext(1);
        verify(observer, times(1)).onCompleted();
        verify(observer, times(0)).onError(any(Throwable.class));
    }

    /**
     * The error from the user provided Observer is not handled by the subscribe method try/catch.
     * 
     * It is handled by the AtomicObserver that wraps the provided Observer.
     * 
     * Result: Passes (if AtomicObserver functionality exists)
     */
    @Test
    public void testCustomObservableWithErrorInObserverAsynchronous() throws InterruptedException {
        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicInteger count = new AtomicInteger();
        final AtomicReference<Throwable> error = new AtomicReference<Throwable>();
        Observable.create(new Func1<Observer<String>, Subscription>() {

            @Override
            public Subscription call(final Observer<String> observer) {
                final BooleanSubscription s = new BooleanSubscription();
                new Thread(new Runnable() {

                    @Override
                    public void run() {
                        try {
                            if (!s.isUnsubscribed()) {
                                observer.onNext("1");
                                observer.onNext("2");
                                observer.onNext("three");
                                observer.onNext("4");
                                observer.onCompleted();
                            }
                        } finally {
                            latch.countDown();
                        }
                    }
                }).start();
                return s;
            }
        }).subscribe(new Observer<String>() {
            @Override
            public void onCompleted() {
                System.out.println("completed");
            }

            @Override
            public void onError(Throwable e) {
                error.set(e);
                System.out.println("error");
                e.printStackTrace();
            }

            @Override
            public void onNext(String v) {
                int num = Integer.parseInt(v);
                System.out.println(num);
                // doSomething(num);
                count.incrementAndGet();
            }

        });

        // wait for async sequence to complete
        latch.await();

        assertEquals(2, count.get());
        assertNotNull(error.get());
        if (!(error.get() instanceof NumberFormatException)) {
            fail("It should be a NumberFormatException");
        }
    }

    /**
     * The error from the user provided Observer is handled by the subscribe try/catch because this is synchronous
     * 
     * Result: Passes
     */
    @Test
    public void testCustomObservableWithErrorInObserverSynchronous() {
        final AtomicInteger count = new AtomicInteger();
        final AtomicReference<Throwable> error = new AtomicReference<Throwable>();
        Observable.create(new Func1<Observer<String>, Subscription>() {

            @Override
            public Subscription call(Observer<String> observer) {
                observer.onNext("1");
                observer.onNext("2");
                observer.onNext("three");
                observer.onNext("4");
                observer.onCompleted();
                return Subscriptions.empty();
            }
        }).subscribe(new Observer<String>() {

            @Override
            public void onCompleted() {
                System.out.println("completed");
            }

            @Override
            public void onError(Throwable e) {
                error.set(e);
                System.out.println("error");
                e.printStackTrace();
            }

            @Override
            public void onNext(String v) {
                int num = Integer.parseInt(v);
                System.out.println(num);
                // doSomething(num);
                count.incrementAndGet();
            }

        });
        assertEquals(2, count.get());
        assertNotNull(error.get());
        if (!(error.get() instanceof NumberFormatException)) {
            fail("It should be a NumberFormatException");
        }
    }

    /**
     * The error from the user provided Observable is handled by the subscribe try/catch because this is synchronous
     * 
     * 
     * Result: Passes
     */
    @Test
    public void testCustomObservableWithErrorInObservableSynchronous() {
        final AtomicInteger count = new AtomicInteger();
        final AtomicReference<Throwable> error = new AtomicReference<Throwable>();
        Observable.create(new Func1<Observer<String>, Subscription>() {

            @Override
            public Subscription call(Observer<String> observer) {
                observer.onNext("1");
                observer.onNext("2");
                throw new NumberFormatException();
            }
        }).subscribe(new Observer<String>() {

            @Override
            public void onCompleted() {
                System.out.println("completed");
            }

            @Override
            public void onError(Throwable e) {
                error.set(e);
                System.out.println("error");
                e.printStackTrace();
            }

            @Override
            public void onNext(String v) {
                System.out.println(v);
                count.incrementAndGet();
            }

        });
        assertEquals(2, count.get());
        assertNotNull(error.get());
        if (!(error.get() instanceof NumberFormatException)) {
            fail("It should be a NumberFormatException");
        }
    }

    @Test
    public void testPublish() throws InterruptedException {
        final AtomicInteger counter = new AtomicInteger();
        ConnectableObservable<String> o = Observable.create(new Func1<Observer<String>, Subscription>() {

            @Override
            public Subscription call(final Observer<String> observer) {
                final BooleanSubscription subscription = new BooleanSubscription();
                new Thread(new Runnable() {

                    @Override
                    public void run() {
                        counter.incrementAndGet();
                        observer.onNext("one");
                        observer.onCompleted();
                    }
                }).start();
                return subscription;
            }
        }).publish();

        final CountDownLatch latch = new CountDownLatch(2);

        // subscribe once
        o.subscribe(new Action1<String>() {

            @Override
            public void call(String v) {
                assertEquals("one", v);
                latch.countDown();
            }
        });

        // subscribe again
        o.subscribe(new Action1<String>() {

            @Override
            public void call(String v) {
                assertEquals("one", v);
                latch.countDown();
            }
        });

        Subscription s = o.connect();
        try {
            if (!latch.await(1000, TimeUnit.MILLISECONDS)) {
                fail("subscriptions did not receive values");
            }
            assertEquals(1, counter.get());
        } finally {
            s.unsubscribe();
        }
    }

    @Test
    public void testReplay() throws InterruptedException {
        final AtomicInteger counter = new AtomicInteger();
        ConnectableObservable<String> o = Observable.create(new Func1<Observer<String>, Subscription>() {

            @Override
            public Subscription call(final Observer<String> observer) {
                final BooleanSubscription subscription = new BooleanSubscription();
                new Thread(new Runnable() {

                    @Override
                    public void run() {
                        counter.incrementAndGet();
                        observer.onNext("one");
                        observer.onCompleted();
                    }
                }).start();
                return subscription;
            }
        }).replay();

        // we connect immediately and it will emit the value
        Subscription s = o.connect();
        try {

            // we then expect the following 2 subscriptions to get that same value
            final CountDownLatch latch = new CountDownLatch(2);

            // subscribe once
            o.subscribe(new Action1<String>() {

                @Override
                public void call(String v) {
                    assertEquals("one", v);
                    latch.countDown();
                }
            });

            // subscribe again
            o.subscribe(new Action1<String>() {

                @Override
                public void call(String v) {
                    assertEquals("one", v);
                    latch.countDown();
                }
            });

            if (!latch.await(1000, TimeUnit.MILLISECONDS)) {
                fail("subscriptions did not receive values");
            }
            assertEquals(1, counter.get());
        } finally {
            s.unsubscribe();
        }
    }

    @Test
    public void testCache() throws InterruptedException {
        final AtomicInteger counter = new AtomicInteger();
        Observable<String> o = Observable.create(new Func1<Observer<String>, Subscription>() {

            @Override
            public Subscription call(final Observer<String> observer) {
                final BooleanSubscription subscription = new BooleanSubscription();
                new Thread(new Runnable() {

                    @Override
                    public void run() {
                        counter.incrementAndGet();
                        observer.onNext("one");
                        observer.onCompleted();
                    }
                }).start();
                return subscription;
            }
        }).cache();

        // we then expect the following 2 subscriptions to get that same value
        final CountDownLatch latch = new CountDownLatch(2);

        // subscribe once
        o.subscribe(new Action1<String>() {

            @Override
            public void call(String v) {
                assertEquals("one", v);
                latch.countDown();
            }
        });

        // subscribe again
        o.subscribe(new Action1<String>() {

            @Override
            public void call(String v) {
                assertEquals("one", v);
                latch.countDown();
            }
        });

        if (!latch.await(1000, TimeUnit.MILLISECONDS)) {
            fail("subscriptions did not receive values");
        }
        assertEquals(1, counter.get());
    }

    /**
     * https://github.com/Netflix/RxJava/issues/198
     * 
     * Rx Design Guidelines 5.2
     * 
     * "when calling the Subscribe method that only has an onNext argument, the OnError behavior will be
     * to rethrow the exception on the thread that the message comes out from the Observable.
     * The OnCompleted behavior in this case is to do nothing."
     */
    @Test
    public void testErrorThrownWithoutErrorHandlerSynchronous() {
        try {
            Observable.error(new RuntimeException("failure")).subscribe(new Action1<Object>() {

                @Override
                public void call(Object t1) {
                    // won't get anything
                }

            });
            fail("expected exception");
        } catch (Throwable e) {
            assertEquals("failure", e.getMessage());
        }
    }

    /**
     * https://github.com/Netflix/RxJava/issues/198
     * 
     * Rx Design Guidelines 5.2
     * 
     * "when calling the Subscribe method that only has an onNext argument, the OnError behavior will be
     * to rethrow the exception on the thread that the message comes out from the Observable.
     * The OnCompleted behavior in this case is to do nothing."
     * 
     * @throws InterruptedException
     */
    @Test
    public void testErrorThrownWithoutErrorHandlerAsynchronous() throws InterruptedException {
        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicReference<Throwable> exception = new AtomicReference<Throwable>();
        Observable.create(new Func1<Observer<String>, Subscription>() {

            @Override
            public Subscription call(final Observer<String> observer) {
                new Thread(new Runnable() {

                    @Override
                    public void run() {
                        try {
                            observer.onError(new Error("failure"));
                        } catch (Throwable e) {
                            // without an onError handler it has to just throw on whatever thread invokes it
                            exception.set(e);
                        }
                        latch.countDown();
                    }
                }).start();
                return Subscriptions.empty();
            }
        }).subscribe(new Action1<String>() {

            @Override
            public void call(String t1) {

            }

        });
        // wait for exception
        latch.await(3000, TimeUnit.MILLISECONDS);
        assertNotNull(exception.get());
        assertEquals("failure", exception.get().getMessage());
    }
}