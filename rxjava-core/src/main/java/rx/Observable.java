/**
 * Copyright 2013 Netflix, Inc.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package rx;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import rx.concurrency.Schedulers;
import rx.observables.BlockingObservable;
import rx.observables.ConnectableObservable;
import rx.observables.GroupedObservable;
import rx.operators.OperationAll;
import rx.operators.OperationBuffer;
import rx.operators.OperationCache;
import rx.operators.OperationCombineLatest;
import rx.operators.OperationConcat;
import rx.operators.OperationDefer;
import rx.operators.OperationDematerialize;
import rx.operators.OperationFilter;
import rx.operators.OperationFinally;
import rx.operators.OperationGroupBy;
import rx.operators.OperationMap;
import rx.operators.OperationMaterialize;
import rx.operators.OperationMerge;
import rx.operators.OperationMergeDelayError;
import rx.operators.OperationMulticast;
import rx.operators.OperationObserveOn;
import rx.operators.OperationOnErrorResumeNextViaFunction;
import rx.operators.OperationOnErrorResumeNextViaObservable;
import rx.operators.OperationOnErrorReturn;
import rx.operators.OperationOnExceptionResumeNextViaObservable;
import rx.operators.OperationSample;
import rx.operators.OperationScan;
import rx.operators.OperationSkip;
import rx.operators.OperationSubscribeOn;
import rx.operators.OperationSwitch;
import rx.operators.OperationSynchronize;
import rx.operators.OperationTake;
import rx.operators.OperationTakeLast;
import rx.operators.OperationTakeUntil;
import rx.operators.OperationTakeWhile;
import rx.operators.OperationTimestamp;
import rx.operators.OperationToObservableFuture;
import rx.operators.OperationToObservableIterable;
import rx.operators.OperationToObservableList;
import rx.operators.OperationToObservableSortedList;
import rx.operators.OperationZip;
import rx.operators.SafeObservableSubscription;
import rx.operators.SafeObserver;
import rx.plugins.RxJavaErrorHandler;
import rx.plugins.RxJavaObservableExecutionHook;
import rx.plugins.RxJavaPlugins;
import rx.subjects.PublishSubject;
import rx.subjects.ReplaySubject;
import rx.subjects.Subject;
import rx.subscriptions.Subscriptions;
import rx.util.BufferClosing;
import rx.util.BufferOpening;
import rx.util.OnErrorNotImplementedException;
import rx.util.Range;
import rx.util.Timestamped;
import rx.util.functions.Action0;
import rx.util.functions.Action1;
import rx.util.functions.Func0;
import rx.util.functions.Func1;
import rx.util.functions.Func2;
import rx.util.functions.Func3;
import rx.util.functions.Func4;
import rx.util.functions.FuncN;
import rx.util.functions.Function;
import rx.util.functions.Functions;

/**
 * The Observable interface that implements the Reactive Pattern.
 * <p>
 * This interface provides overloaded methods for subscribing as well as delegate methods to the
 * various operators.
 * <p>
 * The documentation for this interface makes use of marble diagrams. The following legend explains
 * these diagrams:
 * <p>
 * <img width="640" src="https://github.com/Netflix/RxJava/wiki/images/rx-operators/legend.png">
 * <p>
 * For more information see the <a href="https://github.com/Netflix/RxJava/wiki/Observable">RxJava
 * Wiki</a>
 * 
 * @param <T>
 */
public class Observable<T> {

    //TODO use a consistent parameter naming scheme (for example: for all operators that modify a source Observable, the parameter representing that source Observable should have the same name, e.g. "source" -- currently such parameters are named any of "sequence", "that", "source", "items", or "observable")

    private final static RxJavaObservableExecutionHook hook = RxJavaPlugins.getInstance().getObservableExecutionHook();

    private final Func1<Observer<T>, Subscription> onSubscribe;

    /**
     * Observable with Function to execute when subscribed to.
     * <p>
     * NOTE: Use {@link #create(Func1)} to create an Observable instead of this method unless you
     * specifically have a need for inheritance.
     * 
     * @param onSubscribe
     *            {@link Func1} to be executed when {@link #subscribe(Observer)} is called.
     */
    protected Observable(Func1<Observer<T>, Subscription> onSubscribe) {
        this.onSubscribe = onSubscribe;
    }

    protected Observable() {
        this(null);
        //TODO should this be made private to prevent it? It really serves no good purpose and only confuses things. Unit tests are incorrectly using it today
    }

    /**
     * An {@link Observer} must call an Observable's {@code subscribe} method in order to
     * receive items and notifications from the Observable.
     * 
     * <p>A typical implementation of {@code subscribe} does the following:
     * <p>
     * It stores a reference to the Observer in a collection object, such as a {@code List<T>} object.
     * <p>
     * It returns a reference to the {@link Subscription} interface. This enables Observers to
     * unsubscribe, that is, to stop receiving items and notifications before the Observable stops
     * sending them, which also invokes the Observer's {@link Observer#onCompleted onCompleted} method.
     * <p>
     * An <code>Observable&lt;T&gt;</code> instance is responsible for accepting all subscriptions
     * and notifying all Observers. Unless the documentation for a particular
     * <code>Observable&lt;T&gt;</code> implementation indicates otherwise, Observers should make no
     * assumptions about the order in which multiple Observers will receive their notifications.
     * <p>
     * For more information see the
     * <a href="https://github.com/Netflix/RxJava/wiki/Observable">RxJava Wiki</a>
     * 
     * @param observer
     *            the observer
     * @return a {@link Subscription} reference with which the {@link Observer} can stop receiving items
     *         before the Observable has finished sending them
     * @throws IllegalArgumentException
     *             if the {@link Observer} provided as the argument to {@code subscribe()} is {@code null}
     */
    public Subscription subscribe(Observer<T> observer) {
        // allow the hook to intercept and/or decorate
        Func1<Observer<T>, Subscription> onSubscribeFunction = hook.onSubscribeStart(this, onSubscribe);
        // validate and proceed
        if (observer == null) {
            throw new IllegalArgumentException("observer can not be null");
        }
        if (onSubscribeFunction == null) {
            throw new IllegalStateException("onSubscribe function can not be null.");
            // the subscribe function can also be overridden but generally that's not the appropriate approach so I won't mention that in the exception
        }
        try {
            /**
             * See https://github.com/Netflix/RxJava/issues/216 for discussion on "Guideline 6.4: Protect calls to user code from within an operator"
             */
            if (isInternalImplementation(observer)) {
                Subscription s = onSubscribeFunction.call(observer);
                if (s == null) {
                    // this generally shouldn't be the case on a 'trusted' onSubscribe but in case it happens
                    // we want to gracefully handle it the same as AtomicObservableSubscription does
                    return hook.onSubscribeReturn(this, Subscriptions.empty());
                } else {
                    return hook.onSubscribeReturn(this, s);
                }
            } else {
                SafeObservableSubscription subscription = new SafeObservableSubscription();
                subscription.wrap(onSubscribeFunction.call(new SafeObserver<T>(subscription, observer)));
                return hook.onSubscribeReturn(this, subscription);
            }
        } catch (OnErrorNotImplementedException e) {
            // special handling when onError is not implemented ... we just rethrow
            throw e;
        } catch (Throwable e) {
            // if an unhandled error occurs executing the onSubscribe we will propagate it
            try {
                observer.onError(hook.onSubscribeError(this, e));
            } catch (OnErrorNotImplementedException e2) {
                // special handling when onError is not implemented ... we just rethrow
                throw e2;
            } catch (Throwable e2) {
                // if this happens it means the onError itself failed (perhaps an invalid function implementation)
                // so we are unable to propagate the error correctly and will just throw
                RuntimeException r = new RuntimeException("Error occurred attempting to subscribe [" + e.getMessage() + "] and then again while trying to pass to onError.", e2);
                hook.onSubscribeError(this, r);
                throw r;
            }
            return Subscriptions.empty();
        }
    }

    /**
     * An {@link Observer} must call an Observable's {@code subscribe} method in order to
     * receive items and notifications from the Observable.
     * 
     * <p>A typical implementation of {@code subscribe} does the following:
     * <p>
     * It stores a reference to the Observer in a collection object, such as a {@code List<T>} object.
     * <p>
     * It returns a reference to the {@link Subscription} interface. This enables Observers to
     * unsubscribe, that is, to stop receiving items and notifications before the Observable stops
     * sending them, which also invokes the Observer's {@link Observer#onCompleted onCompleted} method.
     * <p>
     * An {@code Observable<T>} instance is responsible for accepting all subscriptions
     * and notifying all Observers. Unless the documentation for a particular {@code Observable<T>} implementation indicates otherwise, Observers should make no
     * assumptions about the order in which multiple Observers will receive their notifications.
     * <p>
     * For more information see the
     * <a href="https://github.com/Netflix/RxJava/wiki/Observable">RxJava Wiki</a>
     * 
     * @param observer
     *            the observer
     * @param scheduler
     *            the {@link Scheduler} on which Observers subscribe to the Observable
     * @return a {@link Subscription} reference with which Observers can stop receiving items and
     *         notifications before the Observable has finished sending them
     * @throws IllegalArgumentException
     *             if an argument to {@code subscribe()} is {@code null}
     */
    public Subscription subscribe(Observer<T> observer, Scheduler scheduler) {
        return subscribeOn(scheduler).subscribe(observer);
    }

    /**
     * Used for protecting against errors being thrown from Observer implementations and ensuring onNext/onError/onCompleted contract compliance.
     * <p>
     * See https://github.com/Netflix/RxJava/issues/216 for discussion on "Guideline 6.4: Protect calls to user code from within an operator"
     */
    private Subscription protectivelyWrapAndSubscribe(Observer<T> o) {
        SafeObservableSubscription subscription = new SafeObservableSubscription();
        return subscription.wrap(subscribe(new SafeObserver<T>(subscription, o)));
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    public Subscription subscribe(final Map<String, Object> callbacks) {
        if (callbacks == null) {
            throw new RuntimeException("callbacks map can not be null");
        }
        Object _onNext = callbacks.get("onNext");
        if (_onNext == null) {
            throw new RuntimeException("'onNext' key must contain an implementation");
        }
        // lookup and memoize onNext
        final FuncN onNext = Functions.from(_onNext);

        /**
         * Wrapping since raw functions provided by the user are being invoked.
         * 
         * See https://github.com/Netflix/RxJava/issues/216 for discussion on "Guideline 6.4: Protect calls to user code from within an operator"
         */
        return protectivelyWrapAndSubscribe(new Observer() {

            @Override
            public void onCompleted() {
                Object onComplete = callbacks.get("onCompleted");
                if (onComplete != null) {
                    Functions.from(onComplete).call();
                }
            }

            @Override
            public void onError(Throwable e) {
                handleError(e);
                Object onError = callbacks.get("onError");
                if (onError != null) {
                    Functions.from(onError).call(e);
                } else {
                    throw new OnErrorNotImplementedException(e);
                }
            }

            @Override
            public void onNext(Object args) {
                onNext.call(args);
            }

        });
    }

    public Subscription subscribe(final Map<String, Object> callbacks, Scheduler scheduler) {
        return subscribeOn(scheduler).subscribe(callbacks);
    }

    public Subscription subscribe(final Action1<T> onNext) {
        if (onNext == null) {
            throw new IllegalArgumentException("onNext can not be null");
        }

        /**
         * Wrapping since raw functions provided by the user are being invoked.
         * 
         * See https://github.com/Netflix/RxJava/issues/216 for discussion on "Guideline 6.4: Protect calls to user code from within an operator"
         */
        return protectivelyWrapAndSubscribe(new Observer<T>() {

            @Override
            public void onCompleted() {
                // do nothing
            }

            @Override
            public void onError(Throwable e) {
                handleError(e);
                throw new OnErrorNotImplementedException(e);
            }

            @Override
            public void onNext(T args) {
                onNext.call(args);
            }

        });
    }

    public Subscription subscribe(final Action1<T> onNext, Scheduler scheduler) {
        return subscribeOn(scheduler).subscribe(onNext);
    }

    public Subscription subscribe(final Action1<T> onNext, final Action1<Throwable> onError) {
        if (onNext == null) {
            throw new IllegalArgumentException("onNext can not be null");
        }
        if (onError == null) {
            throw new IllegalArgumentException("onError can not be null");
        }

        /**
         * Wrapping since raw functions provided by the user are being invoked.
         * 
         * See https://github.com/Netflix/RxJava/issues/216 for discussion on "Guideline 6.4: Protect calls to user code from within an operator"
         */
        return protectivelyWrapAndSubscribe(new Observer<T>() {

            @Override
            public void onCompleted() {
                // do nothing
            }

            @Override
            public void onError(Throwable e) {
                handleError(e);
                onError.call(e);
            }

            @Override
            public void onNext(T args) {
                onNext.call(args);
            }

        });
    }

    public Subscription subscribe(final Action1<T> onNext, final Action1<Throwable> onError, Scheduler scheduler) {
        return subscribeOn(scheduler).subscribe(onNext, onError);
    }

    public Subscription subscribe(final Action1<T> onNext, final Action1<Throwable> onError, final Action0 onComplete) {
        if (onNext == null) {
            throw new IllegalArgumentException("onNext can not be null");
        }
        if (onError == null) {
            throw new IllegalArgumentException("onError can not be null");
        }
        if (onComplete == null) {
            throw new IllegalArgumentException("onComplete can not be null");
        }

        /**
         * Wrapping since raw functions provided by the user are being invoked.
         * 
         * See https://github.com/Netflix/RxJava/issues/216 for discussion on "Guideline 6.4: Protect calls to user code from within an operator"
         */
        return protectivelyWrapAndSubscribe(new Observer<T>() {

            @Override
            public void onCompleted() {
                onComplete.call();
            }

            @Override
            public void onError(Throwable e) {
                handleError(e);
                onError.call(e);
            }

            @Override
            public void onNext(T args) {
                onNext.call(args);
            }

        });
    }

    public Subscription subscribe(final Action1<T> onNext, final Action1<Throwable> onError, final Action0 onComplete, Scheduler scheduler) {
        return subscribeOn(scheduler).subscribe(onNext, onError, onComplete);
    }

    /**
     * Returns a {@link ConnectableObservable} that upon connection causes the source Observable to
     * push results into the specified subject.
     * 
     * @param subject
     *            the {@link Subject} for the {@link ConnectableObservable} to push source items
     *            into
     * @param <R>
     *            result type
     * @return a {@link ConnectableObservable} that upon connection causes the source Observable to
     *         push results into the specified {@link Subject}
     */
    public <R> ConnectableObservable<R> multicast(Subject<T, R> subject) {
        return OperationMulticast.multicast(this, subject);
    }

    /**
     * Allow the {@link RxJavaErrorHandler} to receive the exception from onError.
     * 
     * @param e
     */
    private void handleError(Throwable e) {
        // onError should be rare so we'll only fetch when needed
        RxJavaPlugins.getInstance().getErrorHandler().handleError(e);
    }

    /**
     * An Observable that never sends any information to an {@link Observer}.
     * 
     * This Observable is useful primarily for testing purposes.
     * 
     * @param <T>
     *            the type of item emitted by the Observable
     */
    private static class NeverObservable<T> extends Observable<T> {
        public NeverObservable() {
            super(new Func1<Observer<T>, Subscription>() {

                @Override
                public Subscription call(Observer<T> t1) {
                    return Subscriptions.empty();
                }

            });
        }
    }

    /**
     * an Observable that invokes {@link Observer#onError onError} when the {@link Observer} subscribes to it.
     * 
     * @param <T>
     *            the type of item emitted by the Observable
     */
    private static class ThrowObservable<T> extends Observable<T> {

        public ThrowObservable(final Throwable exception) {
            super(new Func1<Observer<T>, Subscription>() {

                /**
                 * Accepts an {@link Observer} and calls its {@link Observer#onError onError} method.
                 * 
                 * @param observer
                 *            an {@link Observer} of this Observable
                 * @return a reference to the subscription
                 */
                @Override
                public Subscription call(Observer<T> observer) {
                    observer.onError(exception);
                    return Subscriptions.empty();
                }

            });
        }

    }

    /**
     * Creates an Observable that will execute the given function when an {@link Observer} subscribes to it.
     * <p>
     * <img width="640" src="https://github.com/Netflix/RxJava/wiki/images/rx-operators/create.png">
     * <p>
     * Write the function you pass to <code>create</code> so that it behaves as an Observable: It
     * should invoke the Observer's {@link Observer#onNext onNext}, {@link Observer#onError onError}, and {@link Observer#onCompleted onCompleted} methods
     * appropriately.
     * <p>
     * A well-formed Observable must invoke either the Observer's <code>onCompleted</code> method
     * exactly once or its <code>onError</code> method exactly once.
     * <p>
     * See <a href="http://go.microsoft.com/fwlink/?LinkID=205219">Rx Design Guidelines (PDF)</a>
     * for detailed information.
     * 
     * @param <T>
     *            the type of the items that this Observable emits
     * @param func
     *            a function that accepts an {@code Observer<T>}, invokes its {@code onNext}, {@code onError}, and {@code onCompleted} methods
     *            as appropriate, and returns a {@link Subscription} to allow the Observer to
     *            canceling the subscription
     * @return an Observable that, when an {@link Observer} subscribes to it, will execute the given
     *         function
     */
    public static <T> Observable<T> create(Func1<Observer<T>, Subscription> func) {
        return new Observable<T>(func);
    }

    /**
     * Returns an Observable that emits no data to the {@link Observer} and immediately invokes
     * its {@link Observer#onCompleted onCompleted} method.
     * <p>
     * <img width="640" src="https://raw.github.com/wiki/Netflix/RxJava/images/rx-operators/empty.png">
     * 
     * @param <T>
     *            the type of the items (ostensibly) emitted by the Observable
     * @return an Observable that returns no data to the {@link Observer} and immediately invokes
     *         the {@link Observer}'s {@link Observer#onCompleted() onCompleted} method
     */
    public static <T> Observable<T> empty() {
        return from(new ArrayList<T>());
    }

    /**
     * Returns an Observable that invokes an {@link Observer}'s {@link Observer#onError onError} method when the Observer subscribes to it
     * <p>
     * <img width="640" src="https://raw.github.com/wiki/Netflix/RxJava/images/rx-operators/error.png">
     * 
     * @param exception
     *            the particular error to report
     * @param <T>
     *            the type of the items (ostensibly) emitted by the Observable
     * @return an Observable that invokes the {@link Observer}'s {@link Observer#onError onError} method when the Observer subscribes to it
     */
    public static <T> Observable<T> error(Throwable exception) {
        return new ThrowObservable<T>(exception);
    }

    /**
     * Converts an {@link Iterable} sequence into an Observable.
     * <p>
     * <img width="640" src="https://github.com/Netflix/RxJava/wiki/images/rx-operators/from.png">
     * 
     * <p>Implementation note: the entire iterable sequence will be immediately emitted each time an {@link Observer} subscribes. Since this occurs before the {@link Subscription} is returned,
     * it in not possible to unsubscribe from the sequence before it completes.
     * 
     * @param iterable
     *            the source {@link Iterable} sequence
     * @param <T>
     *            the type of items in the {@link Iterable} sequence and the type of items to be
     *            emitted by the resulting Observable
     * @return an Observable that emits each item in the source {@link Iterable} sequence
     */
    public static <T> Observable<T> from(Iterable<T> iterable) {
        return create(OperationToObservableIterable.toObservableIterable(iterable));
    }

    /**
     * Converts an Array into an Observable.
     * <p>
     * <img width="640" src="https://github.com/Netflix/RxJava/wiki/images/rx-operators/from.png">
     * 
     * <p>Implementation note: the entire array will be immediately emitted each time an {@link Observer} subscribes. Since this occurs before the {@link Subscription} is returned,
     * it in not possible to unsubscribe from the sequence before it completes.
     * 
     * @param items
     *            the source Array
     * @param <T>
     *            the type of items in the Array, and the type of items to be emitted by the
     *            resulting Observable
     * @return an Observable that emits each item in the source Array
     */
    public static <T> Observable<T> from(T... items) {
        return create(OperationToObservableIterable.toObservableIterable(Arrays.asList(items)));
    }

    /**
     * Generates an Observable that emits a sequence of integers within a specified range.
     * <p>
     * <img width="640" src="https://github.com/Netflix/RxJava/wiki/images/rx-operators/range.png">
     * 
     * <p>Implementation note: the entire range will be immediately emitted each time an {@link Observer} subscribes. Since this occurs before the {@link Subscription} is returned,
     * it in not possible to unsubscribe from the sequence before it completes.
     * 
     * @param start
     *            the value of the first integer in the sequence
     * @param count
     *            the number of sequential integers to generate
     * 
     * @return an Observable that emits a range of sequential integers
     * 
     * @see <a href="http://msdn.microsoft.com/en-us/library/hh229460(v=vs.103).aspx">Observable.Range Method (Int32, Int32)</a>
     */
    public static Observable<Integer> range(int start, int count) {
        return from(Range.createWithCount(start, count));
    }

    /**
     * Returns an Observable that calls an Observable factory to create its Observable for each
     * new Observer that subscribes. That is, for each subscriber, the actuall Observable is determined
     * by the factory function.
     * 
     * <p>
     * <img width="640" src="https://github.com/Netflix/RxJava/wiki/images/rx-operators/defer.png">
     * <p>
     * The defer operator allows you to defer or delay emitting items from an Observable until such
     * time as an Observer subscribes to the Observable. This allows an {@link Observer} to easily
     * obtain updates or a refreshed version of the sequence.
     * 
     * @param observableFactory
     *            the Observable factory function to invoke for each {@link Observer} that
     *            subscribes to the resulting Observable
     * @param <T>
     *            the type of the items emitted by the Observable
     * @return an Observable whose {@link Observer}s trigger an invocation of the given Observable
     *         factory function
     */
    public static <T> Observable<T> defer(Func0<Observable<T>> observableFactory) {
        return create(OperationDefer.defer(observableFactory));
    }

    /**
     * Returns an Observable that emits a single item and then completes.
     * <p>
     * <img width="640" src="https://raw.github.com/wiki/Netflix/RxJava/images/rx-operators/just.png">
     * <p>
     * To convert any object into an Observable that emits that object, pass that object into the
     * <code>just</code> method.
     * <p>
     * This is similar to the {@link #from(java.lang.Object[])} method, except that
     * <code>from()</code> will convert an {@link Iterable} object into an Observable that emits
     * each of the items in the Iterable, one at a time, while the <code>just()</code> method
     * converts an Iterable into an Observable that emits the entire Iterable as a single item.
     * 
     * @param value
     *            the item to pass to the {@link Observer}'s {@link Observer#onNext onNext} method
     * @param <T>
     *            the type of that item
     * @return an Observable that emits a single item and then completes
     */
    public static <T> Observable<T> just(T value) {
        List<T> list = new ArrayList<T>();
        list.add(value);

        return from(list);
    }

    /**
     * Flattens a list of Observables into one Observable, without any transformation.
     * <p>
     * <img width="640" src="https://raw.github.com/wiki/Netflix/RxJava/images/rx-operators/merge.png">
     * <p>
     * You can combine the items emitted by multiple Observables so that they act like a single
     * Observable, by using the <code>merge</code> method.
     * 
     * @param source
     *            a list of Observables
     * @return an Observable that emits items that are the result of flattening the {@code source} list of Observables
     * @see <a href="http://msdn.microsoft.com/en-us/library/hh229099(v=vs.103).aspx">MSDN: Observable.Merge</a>
     */
    public static <T> Observable<T> merge(List<Observable<T>> source) {
        return create(OperationMerge.merge(source));
    }

    /**
     * Flattens a sequence of Observables emitted by an Observable into one Observable, without any
     * transformation.
     * <p>
     * <img width="640" src="https://raw.github.com/wiki/Netflix/RxJava/images/rx-operators/merge.png">
     * <p>
     * You can combine the items emitted by multiple Observables so that they act like a single
     * Observable, by using the {@code merge} method.
     * 
     * @param source
     *            an Observable that emits Observables
     * @return an Observable that emits items that are the result of flattening the items emitted
     *         by the Observables emitted by the {@code source} Observable
     * @see <a href="http://msdn.microsoft.com/en-us/library/hh229099(v=vs.103).aspx">MSDN: Observable.Merge Method</a>
     */
    public static <T> Observable<T> merge(Observable<Observable<T>> source) {
        return create(OperationMerge.merge(source));
    }

    /**
     * Flattens a series of Observables into one Observable, without any transformation.
     * <p>
     * <img width="640" src="https://raw.github.com/wiki/Netflix/RxJava/images/rx-operators/merge.png">
     * <p>
     * You can combine items emitted by multiple Observables so that they act like a single
     * Observable, by using the {@code merge} method.
     * 
     * @param source
     *            a series of Observables
     * @return an Observable that emits items that are the result of flattening the items emitted
     *         by the {@code source} Observables
     * @see <a href="http://msdn.microsoft.com/en-us/library/hh229099(v=vs.103).aspx">MSDN: Observable.Merge Method</a>
     */
    public static <T> Observable<T> merge(Observable<T>... source) {
        return create(OperationMerge.merge(source));
    }

    /**
     * Returns an Observable that emits the items emitted by two or more Observables, one after the
     * other.
     * <p>
     * <img width="640" src="https://raw.github.com/wiki/Netflix/RxJava/images/rx-operators/concat.png">
     * 
     * @param source
     *            a series of Observables
     * @return an Observable that emits items that are the result of combining the items emitted by
     *         the {@code source} Observables, one after the other
     * @see <a href="http://msdn.microsoft.com/en-us/library/system.reactive.linq.observable.concat(v=vs.103).aspx">MSDN: Observable.Concat Method</a>
     */
    public static <T> Observable<T> concat(Observable<T>... source) {
        return create(OperationConcat.concat(source));
    }

    /**
     * This behaves like {@link #merge(java.util.List)} except that if any of the merged Observables
     * notify of an error via {@link Observer#onError onError}, {@code mergeDelayError} will
     * refrain from propagating that error notification until all of the merged Observables have
     * finished emitting items.
     * <p>
     * <img width="640" src="https://raw.github.com/wiki/Netflix/RxJava/images/rx-operators/mergeDelayError.png">
     * <p>
     * Even if multiple merged Observables send {@code onError} notifications, {@code mergeDelayError} will only invoke the {@code onError} method of its
     * Observers once.
     * <p>
     * This method allows an Observer to receive all successfully emitted items from all of the
     * source Observables without being interrupted by an error notification from one of them.
     * 
     * @param source
     *            a list of Observables
     * @return an Observable that emits items that are the result of flattening the items emitted by
     *         the {@code source} list of Observables
     * @see <a href="http://msdn.microsoft.com/en-us/library/hh229099(v=vs.103).aspx">MSDN: Observable.Merge Method</a>
     */
    public static <T> Observable<T> mergeDelayError(List<Observable<T>> source) {
        return create(OperationMergeDelayError.mergeDelayError(source));
    }

    /**
     * This behaves like {@link #merge(Observable)} except that if any of the merged Observables
     * notify of an error via {@link Observer#onError onError}, {@code mergeDelayError} will
     * refrain from propagating that error notification until all of the merged Observables have
     * finished emitting items.
     * <p>
     * <img width="640" src="https://raw.github.com/wiki/Netflix/RxJava/images/rx-operators/mergeDelayError.png">
     * <p>
     * Even if multiple merged Observables send {@code onError} notifications, {@code mergeDelayError} will only invoke the {@code onError} method of its
     * Observers once.
     * <p>
     * This method allows an Observer to receive all successfully emitted items from all of the
     * source Observables without being interrupted by an error notification from one of them.
     * 
     * @param source
     *            an Observable that emits Observables
     * @return an Observable that emits items that are the result of flattening the items emitted by
     *         the Observables emitted by the {@code source} Observable
     * @see <a href="http://msdn.microsoft.com/en-us/library/hh229099(v=vs.103).aspx">MSDN: Observable.Merge Method</a>
     */
    public static <T> Observable<T> mergeDelayError(Observable<Observable<T>> source) {
        return create(OperationMergeDelayError.mergeDelayError(source));
    }

    /**
     * This behaves like {@link #merge(Observable...)} except that if any of the merged Observables
     * notify of an error via {@link Observer#onError onError}, {@code mergeDelayError} will
     * refrain from propagating that error notification until all of the merged Observables have
     * finished emitting items.
     * <p>
     * <img width="640" src="https://raw.github.com/wiki/Netflix/RxJava/images/rx-operators/mergeDelayError.png">
     * <p>
     * Even if multiple merged Observables send {@code onError} notifications, {@code mergeDelayError} will only invoke the {@code onError} method of its
     * Observers once.
     * <p>
     * This method allows an Observer to receive all successfully emitted items from all of the
     * source Observables without being interrupted by an error notification from one of them.
     * 
     * @param source
     *            a series of Observables
     * @return an Observable that emits items that are the result of flattening the items emitted by
     *         the {@code source} Observables
     * @see <a href="http://msdn.microsoft.com/en-us/library/hh229099(v=vs.103).aspx">MSDN: Observable.Merge Method</a>
     */
    public static <T> Observable<T> mergeDelayError(Observable<T>... source) {
        return create(OperationMergeDelayError.mergeDelayError(source));
    }

    /**
     * Returns an Observable that never sends any items or notifications to an {@link Observer}.
     * <p>
     * <img width="640" src="https://github.com/Netflix/RxJava/wiki/images/rx-operators/never.png">
     * <p>
     * This Observable is useful primarily for testing purposes.
     * 
     * @param <T>
     *            the type of items (not) emitted by the Observable
     * @return an Observable that never sends any items or notifications to an {@link Observer}
     */
    public static <T> Observable<T> never() {
        return new NeverObservable<T>();
    }

    /**
     * Given an Observable that emits Observables, creates a single Observable that
     * emits the items emitted by the most recently published of those Observables.
     * <p>
     * <img width="640" src="https://github.com/Netflix/RxJava/wiki/images/rx-operators/switchDo.png">
     * 
     * @param sequenceOfSequences
     *            the source Observable that emits Observables
     * @return an Observable that emits only the items emitted by the most recently published
     *         Observable
     */
    public static <T> Observable<T> switchDo(Observable<Observable<T>> sequenceOfSequences) {
        // TODO should this static remain? I have left it because it is an Observable<Observable>
        return create(OperationSwitch.switchDo(sequenceOfSequences));
    }

    /**
     * Given an Observable that emits Observables, creates a single Observable that
     * emits the items emitted by the most recently published of those Observables.
     * <p>
     * <img width="640" src="https://github.com/Netflix/RxJava/wiki/images/rx-operators/switchDo.png">
     * 
     * @param sequenceOfSequences
     *            the source Observable that emits Observables
     * @return an Observable that emits only the items emitted by the most recently published
     *         Observable
     * @throws ClassCastException
     *             if sequence not of type {@code Observable<Observable<T>}
     */
    @SuppressWarnings("unchecked")
    public Observable<T> switchDo() {
        // TODO can we come up with a better name than this? It should be 'switch' but that is reserved.
        // Perhaps 'switchOnNext'?
        return create(OperationSwitch.switchDo((Observable<Observable<T>>) this));
    }

    /**
     * Accepts an Observable and wraps it in another Observable that ensures that the resulting
     * Observable is chronologically well-behaved.
     * <p>
     * <img width="640" src="https://github.com/Netflix/RxJava/wiki/images/rx-operators/synchronize.png">
     * <p>
     * A well-behaved Observable does not interleave its invocations of the {@link Observer#onNext onNext}, {@link Observer#onCompleted onCompleted}, and {@link Observer#onError onError} methods of
     * its {@link Observer}s; it invokes {@code onCompleted} or {@code onError} only once; and it never invokes {@code onNext} after invoking either {@code onCompleted} or {@code onError}.
     * {@code synchronize} enforces this, and the Observable it returns invokes {@code onNext} and {@code onCompleted} or {@code onError} synchronously.
     * 
     * @param observable
     *            the source Observable
     * @param <T>
     *            the type of item emitted by the source Observable
     * @return an Observable that is a chronologically well-behaved version of the source
     *         Observable, and that synchronously notifies its {@link Observer}s
     */
    public static <T> Observable<T> synchronize(Observable<T> observable) {
        return create(OperationSynchronize.synchronize(observable));
    }

    /**
     * Wraps each item emitted by a source Observable in a {@link Timestamped} object.
     * <p>
     * <img width="640" src="https://github.com/Netflix/RxJava/wiki/images/rx-operators/timestamp.png">
     * 
     * @return an Observable that emits timestamped items from the source Observable
     */
    public Observable<Timestamped<T>> timestamp() {
        return create(OperationTimestamp.timestamp(this));
    }

    /**
     * Converts a {@link Future} into an Observable.
     * <p>
     * <img width="640" src="https://github.com/Netflix/RxJava/wiki/images/rx-operators/from.Future.png">
     * <p>
     * You can convert any object that supports the {@link Future} interface into an Observable that
     * emits the return value of the {@link Future#get} method of that object, by passing the
     * object into the {@code from} method.
     * <p>
     * <em>Important note:</em> This Observable is blocking; you cannot unsubscribe from it.
     * 
     * @param future
     *            the source {@link Future}
     * @param <T>
     *            the type of object that the {@link Future} returns, and also the type of item to
     *            be emitted by the resulting Observable
     * @return an Observable that emits the item from the source Future
     */
    public static <T> Observable<T> from(Future<T> future) {
        return create(OperationToObservableFuture.toObservableFuture(future));
    }

    /**
     * Converts a {@link Future} into an Observable.
     * <p>
     * <img width="640" src="https://github.com/Netflix/RxJava/wiki/images/rx-operators/from.Future.png">
     * <p>
     * You can convert any object that supports the {@link Future} interface into an Observable that
     * emits the return value of the {@link Future#get} method of that object, by passing the
     * object into the {@code from} method.
     * <p>
     * 
     * @param future
     *            the source {@link Future}
     * @param scheduler
     *            the {@link Scheduler} to wait for the Future on. Use a Scheduler such as {@link Schedulers#threadPoolForIO()} that can block and wait on the future.
     * @param <T>
     *            the type of object that the {@link Future} returns, and also the type of item to
     *            be emitted by the resulting Observable
     * @return an Observable that emits the item from the source Future
     */
    public static <T> Observable<T> from(Future<T> future, Scheduler scheduler) {
        return create(OperationToObservableFuture.toObservableFuture(future)).subscribeOn(scheduler);
    }

    /**
     * Converts a {@link Future} into an Observable with timeout.
     * <p>
     * <img width="640" src="https://github.com/Netflix/RxJava/wiki/images/rx-operators/from.Future.png">
     * <p>
     * You can convert any object that supports the {@link Future} interface into an Observable that
     * emits the return value of the {link Future#get} method of that object, by passing the
     * object into the {@code from} method.
     * <p>
     * <em>Important note:</em> This Observable is blocking; you cannot unsubscribe from it.
     * 
     * @param future
     *            the source {@link Future}
     * @param timeout
     *            the maximum time to wait before calling <code>get()</code>
     * @param unit
     *            the {@link TimeUnit} of the time argument
     * @param <T>
     *            the type of object that the {@link Future} returns, and also the type of item to
     *            be emitted by the resulting Observable
     * @return an Observable that emits the item from the source {@link Future}
     */
    public static <T> Observable<T> from(Future<T> future, long timeout, TimeUnit unit) {
        return create(OperationToObservableFuture.toObservableFuture(future, timeout, unit));
    }

    /**
     * <p>
     * <img width="640" src="https://raw.github.com/wiki/Netflix/RxJava/images/rx-operators/zip.png">
     * <p> {@code zip} applies this function in strict sequence, so the first item emitted by the
     * new Observable will be the result of the function applied to the first item emitted by {@code w0} and the first item emitted by {@code w1}; the second item emitted by
     * the new Observable will be the result of the function applied to the second item emitted by {@code w0} and the second item emitted by {@code w1}; and so forth.
     * <p>
     * The resulting {@code Observable<R>} returned from {@code zip} will invoke {@link Observer#onNext onNext} as many times as the number of {@code onNext} invocations
     * of the source Observable that emits the fewest items.
     * 
     * @param w0
     *            one source Observable
     * @param w1
     *            another source Observable
     * @param reduceFunction
     *            a function that, when applied to a pair of items, each emitted by one of the two
     *            source Observables, results in an item that will be emitted by the resulting
     *            Observable
     * @return an Observable that emits the zipped results
     */
    public static <R, T0, T1> Observable<R> zip(Observable<T0> w0, Observable<T1> w1, Func2<T0, T1, R> reduceFunction) {
        return create(OperationZip.zip(w0, w1, reduceFunction));
    }

    /**
     * Returns an Observable that emits Boolean values that indicate whether the pairs of items
     * emitted by two source Observables are equal.
     * <p>
     * <img width="640" src="https://github.com/Netflix/RxJava/wiki/images/rx-operators/sequenceEqual.png">
     * 
     * @param first
     *            one Observable to compare
     * @param second
     *            the second Observable to compare
     * @param <T>
     *            the type of items emitted by each Observable
     * @return an Observable that emits Booleans that indicate whether the corresponding items
     *         emitted by the source Observables are equal
     */
    public static <T> Observable<Boolean> sequenceEqual(Observable<T> first, Observable<T> second) {
        return sequenceEqual(first, second, new Func2<T, T, Boolean>() {
            @Override
            public Boolean call(T first, T second) {
                return first.equals(second);
            }
        });
    }

    /**
     * Returns an Observable that emits Boolean values that indicate whether the pairs of items
     * emitted by two source Observables are equal based on the results of a specified equality
     * function.
     * <p>
     * <img width="640" src="https://github.com/Netflix/RxJava/wiki/images/rx-operators/sequenceEqual.png">
     * 
     * @param first
     *            one Observable to compare
     * @param second
     *            the second Observable to compare
     * @param equality
     *            a function used to compare items emitted by both Observables
     * @param <T>
     *            the type of items emitted by each Observable
     * @return an Observable that emits Booleans that indicate whether the corresponding items
     *         emitted by the source Observables are equal
     */
    public static <T> Observable<Boolean> sequenceEqual(Observable<T> first, Observable<T> second, Func2<T, T, Boolean> equality) {
        return zip(first, second, equality);
    }

    /**
     * Returns an Observable that emits the results of a function of your choosing applied to
     * combinations of three items emitted, in sequence, by three other Observables.
     * <p>
     * <img width="640" src="https://raw.github.com/wiki/Netflix/RxJava/images/rx-operators/zip.png">
     * <p> {@code zip} applies this function in strict sequence, so the first item emitted by the
     * new Observable will be the result of the function applied to the first item emitted by {@code w0}, the first item emitted by {@code w1}, and the first item emitted by {@code w2}; the second
     * item emitted by the new Observable will be the result of the
     * function applied to the second item emitted by {@code w0}, the second item emitted by {@code w1}, and the second item emitted by {@code w2}; and so forth.
     * <p>
     * The resulting {@code Observable<R>} returned from {@code zip} will invoke {@link Observer#onNext onNext} as many times as the number of {@code onNext} invocations
     * of the source Observable that emits the fewest items.
     * 
     * @param w0
     *            one source Observable
     * @param w1
     *            another source Observable
     * @param w2
     *            a third source Observable
     * @param function
     *            a function that, when applied to an item emitted by each of the source
     *            Observables, results in an item that will be emitted by the resulting Observable
     * @return an Observable that emits the zipped results
     */
    public static <R, T0, T1, T2> Observable<R> zip(Observable<T0> w0, Observable<T1> w1, Observable<T2> w2, Func3<T0, T1, T2, R> function) {
        return create(OperationZip.zip(w0, w1, w2, function));
    }

    /**
     * Returns an Observable that emits the results of a function of your choosing applied to
     * combinations of four items emitted, in sequence, by four other Observables.
     * <p>
     * <img width="640" src="https://raw.github.com/wiki/Netflix/RxJava/images/rx-operators/zip.png">
     * <p> {@code zip} applies this function in strict sequence, so the first item emitted by the
     * new Observable will be the result of the function applied to the first item emitted by {@code w0}, the first item emitted by {@code w1}, the first item emitted by {@code w2}, and the first item
     * emitted by {@code w3}; the second item emitted by
     * the new Observable will be the result of the function applied to the second item emitted by
     * each of those Observables; and so forth.
     * <p>
     * The resulting {@code Observable<R>} returned from {@code zip} will invoke {@link Observer#onNext onNext} as many times as the number of {@code onNext} invocations
     * of the source Observable that emits the fewest items.
     * 
     * @param w0
     *            one source Observable
     * @param w1
     *            another source Observable
     * @param w2
     *            a third source Observable
     * @param w3
     *            a fourth source Observable
     * @param reduceFunction
     *            a function that, when applied to an item emitted by each of the source
     *            Observables, results in an item that will be emitted by the resulting Observable
     * @return an Observable that emits the zipped results
     */
    public static <R, T0, T1, T2, T3> Observable<R> zip(Observable<T0> w0, Observable<T1> w1, Observable<T2> w2, Observable<T3> w3, Func4<T0, T1, T2, T3, R> reduceFunction) {
        return create(OperationZip.zip(w0, w1, w2, w3, reduceFunction));
    }

    /**
     * Combines the given observables, emitting an event containing an aggregation of the latest values of each of the source observables
     * each time an event is received from one of the source observables, where the aggregation is defined by the given function.
     * <p>
     * <img width="640" src="https://github.com/Netflix/RxJava/wiki/images/rx-operators/combineLatest.png">
     *
     * @param w0
     * The first source observable.
     * @param w1
     * The second source observable.
     * @param combineFunction
     * The aggregation function used to combine the source observable values.
     * @return An Observable that combines the source Observables with the given combine function
     */
    public static <R, T0, T1> Observable<R> combineLatest(Observable<T0> w0, Observable<T1> w1, Func2<T0, T1, R> combineFunction) {
        return create(OperationCombineLatest.combineLatest(w0, w1, combineFunction));
    }

    /**
     * @see #combineLatest(Observable, Observable, Func2)
     */
    public static <R, T0, T1, T2> Observable<R> combineLatest(Observable<T0> w0, Observable<T1> w1, Observable<T2> w2, Func3<T0, T1, T2, R> combineFunction) {
        return create(OperationCombineLatest.combineLatest(w0, w1, w2, combineFunction));
    }

    /**
     * @see #combineLatest(Observable, Observable, Func2)
     */
    public static <R, T0, T1, T2, T3> Observable<R> combineLatest(Observable<T0> w0, Observable<T1> w1, Observable<T2> w2, Observable<T3> w3, Func4<T0, T1, T2, T3, R> combineFunction) {
        return create(OperationCombineLatest.combineLatest(w0, w1, w2, w3, combineFunction));
    }
    
    /**
     * Creates an Observable which produces buffers of collected values.
     * 
     * <p>This Observable produces connected non-overlapping buffers. The current buffer is
     * emitted and replaced with a new buffer when the Observable produced by the specified {@link Func0} produces a {@link BufferClosing} object. The * {@link Func0} will then
     * be used to create a new Observable to listen for the end of the next buffer.
     * 
     * @param bufferClosingSelector
     *            The {@link Func0} which is used to produce an {@link Observable} for every buffer created.
     *            When this {@link Observable} produces a {@link BufferClosing} object, the associated buffer
     *            is emitted and replaced with a new one.
     * @return
     *         An {@link Observable} which produces connected non-overlapping buffers, which are emitted
     *         when the current {@link Observable} created with the {@link Func0} argument produces a {@link BufferClosing} object.
     */
    public Observable<List<T>> buffer(Func0<Observable<BufferClosing>> bufferClosingSelector) {
        return create(OperationBuffer.buffer(this, bufferClosingSelector));
    }

    /**
     * Creates an Observable which produces buffers of collected values.
     * 
     * <p>This Observable produces buffers. Buffers are created when the specified "bufferOpenings"
     * Observable produces a {@link BufferOpening} object. Additionally the {@link Func0} argument
     * is used to create an Observable which produces {@link BufferClosing} objects. When this
     * Observable produces such an object, the associated buffer is emitted.
     * 
     * @param bufferOpenings
     *            The {@link Observable} which, when it produces a {@link BufferOpening} object, will cause
     *            another buffer to be created.
     * @param bufferClosingSelector
     *            The {@link Func0} which is used to produce an {@link Observable} for every buffer created.
     *            When this {@link Observable} produces a {@link BufferClosing} object, the associated buffer
     *            is emitted.
     * @return
     *         An {@link Observable} which produces buffers which are created and emitted when the specified {@link Observable}s publish certain objects.
     */
    public Observable<List<T>> buffer(Observable<BufferOpening> bufferOpenings, Func1<BufferOpening, Observable<BufferClosing>> bufferClosingSelector) {
        return create(OperationBuffer.buffer(this, bufferOpenings, bufferClosingSelector));
    }

    /**
     * Creates an Observable which produces buffers of collected values.
     * 
     * <p>This Observable produces connected non-overlapping buffers, each containing "count"
     * elements. When the source Observable completes or encounters an error, the current
     * buffer is emitted, and the event is propagated.
     * 
     * @param count
     *            The maximum size of each buffer before it should be emitted.
     * @return
     *         An {@link Observable} which produces connected non-overlapping buffers containing at most
     *         "count" produced values.
     */
    public Observable<List<T>> buffer(int count) {
        return create(OperationBuffer.buffer(this, count));
    }

    /**
     * Creates an Observable which produces buffers of collected values.
     * 
     * <p>This Observable produces buffers every "skip" values, each containing "count"
     * elements. When the source Observable completes or encounters an error, the current
     * buffer is emitted, and the event is propagated.
     * 
     * @param count
     *            The maximum size of each buffer before it should be emitted.
     * @param skip
     *            How many produced values need to be skipped before starting a new buffer. Note that when "skip" and
     *            "count" are equals that this is the same operation as {@link Observable#buffer(Observable, int)}.
     * @return
     *         An {@link Observable} which produces buffers every "skipped" values containing at most
     *         "count" produced values.
     */
    public Observable<List<T>> buffer(int count, int skip) {
        return create(OperationBuffer.buffer(this, count, skip));
    }

    /**
     * Creates an Observable which produces buffers of collected values.
     * 
     * <p>This Observable produces connected non-overlapping buffers, each of a fixed duration
     * specified by the "timespan" argument. When the source Observable completes or encounters
     * an error, the current buffer is emitted and the event is propagated.
     * 
     * @param timespan
     *            The period of time each buffer is collecting values before it should be emitted, and
     *            replaced with a new buffer.
     * @param unit
     *            The unit of time which applies to the "timespan" argument.
     * @return
     *         An {@link Observable} which produces connected non-overlapping buffers with a fixed duration.
     */
    public Observable<List<T>> buffer(long timespan, TimeUnit unit) {
        return create(OperationBuffer.buffer(this, timespan, unit));
    }

    /**
     * Creates an Observable which produces buffers of collected values.
     * 
     * <p>This Observable produces connected non-overlapping buffers, each of a fixed duration
     * specified by the "timespan" argument. When the source Observable completes or encounters
     * an error, the current buffer is emitted and the event is propagated.
     * 
     * @param timespan
     *            The period of time each buffer is collecting values before it should be emitted, and
     *            replaced with a new buffer.
     * @param unit
     *            The unit of time which applies to the "timespan" argument.
     * @param scheduler
     *            The {@link Scheduler} to use when determining the end and start of a buffer.
     * @return
     *         An {@link Observable} which produces connected non-overlapping buffers with a fixed duration.
     */
    public Observable<List<T>> buffer(long timespan, TimeUnit unit, Scheduler scheduler) {
        return create(OperationBuffer.buffer(this, timespan, unit, scheduler));
    }

    /**
     * Creates an Observable which produces buffers of collected values. This Observable produces connected
     * non-overlapping buffers, each of a fixed duration specified by the "timespan" argument or a maximum size
     * specified by the "count" argument (which ever is reached first). When the source Observable completes
     * or encounters an error, the current buffer is emitted and the event is propagated.
     * 
     * @param timespan
     *            The period of time each buffer is collecting values before it should be emitted, and
     *            replaced with a new buffer.
     * @param unit
     *            The unit of time which applies to the "timespan" argument.
     * @param count
     *            The maximum size of each buffer before it should be emitted.
     * @return
     *         An {@link Observable} which produces connected non-overlapping buffers which are emitted after
     *         a fixed duration or when the buffer has reached maximum capacity (which ever occurs first).
     */
    public Observable<List<T>> buffer(long timespan, TimeUnit unit, int count) {
        return create(OperationBuffer.buffer(this, timespan, unit, count));
    }

    /**
     * Creates an Observable which produces buffers of collected values. This Observable produces connected
     * non-overlapping buffers, each of a fixed duration specified by the "timespan" argument or a maximum size
     * specified by the "count" argument (which ever is reached first). When the source Observable completes
     * or encounters an error, the current buffer is emitted and the event is propagated.
     * 
     * @param timespan
     *            The period of time each buffer is collecting values before it should be emitted, and
     *            replaced with a new buffer.
     * @param unit
     *            The unit of time which applies to the "timespan" argument.
     * @param count
     *            The maximum size of each buffer before it should be emitted.
     * @param scheduler
     *            The {@link Scheduler} to use when determining the end and start of a buffer.
     * @return
     *         An {@link Observable} which produces connected non-overlapping buffers which are emitted after
     *         a fixed duration or when the buffer has reached maximum capacity (which ever occurs first).
     */
    public Observable<List<T>> buffer(long timespan, TimeUnit unit, int count, Scheduler scheduler) {
        return create(OperationBuffer.buffer(this, timespan, unit, count, scheduler));
    }

    /**
     * Creates an Observable which produces buffers of collected values. This Observable starts a new buffer
     * periodically, which is determined by the "timeshift" argument. Each buffer is emitted after a fixed timespan
     * specified by the "timespan" argument. When the source Observable completes or encounters an error, the
     * current buffer is emitted and the event is propagated.
     * 
     * @param timespan
     *            The period of time each buffer is collecting values before it should be emitted.
     * @param timeshift
     *            The period of time after which a new buffer will be created.
     * @param unit
     *            The unit of time which applies to the "timespan" and "timeshift" argument.
     * @return
     *         An {@link Observable} which produces new buffers periodically, and these are emitted after
     *         a fixed timespan has elapsed.
     */
    public Observable<List<T>> buffer(long timespan, long timeshift, TimeUnit unit) {
        return create(OperationBuffer.buffer(this, timespan, timeshift, unit));
    }

    /**
     * Creates an Observable which produces buffers of collected values. This Observable starts a new buffer
     * periodically, which is determined by the "timeshift" argument. Each buffer is emitted after a fixed timespan
     * specified by the "timespan" argument. When the source Observable completes or encounters an error, the
     * current buffer is emitted and the event is propagated.
     * 
     * @param timespan
     *            The period of time each buffer is collecting values before it should be emitted.
     * @param timeshift
     *            The period of time after which a new buffer will be created.
     * @param unit
     *            The unit of time which applies to the "timespan" and "timeshift" argument.
     * @param scheduler
     *            The {@link Scheduler} to use when determining the end and start of a buffer.
     * @return
     *         An {@link Observable} which produces new buffers periodically, and these are emitted after
     *         a fixed timespan has elapsed.
     */
    public Observable<List<T>> buffer(long timespan, long timeshift, TimeUnit unit, Scheduler scheduler) {
        return create(OperationBuffer.buffer(this, timespan, timeshift, unit, scheduler));
    }

    /**
     * Returns an Observable that emits the results of a function of your choosing applied to
     * combinations of four items emitted, in sequence, by four other Observables.
     * <p> {@code zip} applies this function in strict sequence, so the first item emitted by the
     * new Observable will be the result of the function applied to the first item emitted by
     * all of the Observalbes; the second item emitted by the new Observable will be the result of
     * the function applied to the second item emitted by each of those Observables; and so forth.
     * <p>
     * The resulting {@code Observable<R>} returned from {@code zip} will invoke {@code onNext} as many times as the number of {@code onNext} invokations of the
     * source Observable that emits the fewest items.
     * <p>
     * <img width="640" src="https://raw.github.com/wiki/Netflix/RxJava/images/rx-operators/zip.png">
     * 
     * @param ws
     *            An Observable of source Observables
     * @param reduceFunction
     *            a function that, when applied to an item emitted by each of the source
     *            Observables, results in an item that will be emitted by the resulting Observable
     * @return an Observable that emits the zipped results
     */
    public static <R> Observable<R> zip(Observable<Observable<?>> ws, final FuncN<R> reduceFunction) {
        return ws.toList().mapMany(new Func1<List<Observable<?>>, Observable<R>>() {
            @Override
            public Observable<R> call(List<Observable<?>> wsList) {
                return create(OperationZip.zip(wsList, reduceFunction));
            }
        });
    }

    /**
     * Returns an Observable that emits the results of a function of your choosing applied to
     * combinations of four items emitted, in sequence, by four other Observables.
     * <p> {@code zip} applies this function in strict sequence, so the first item emitted by the
     * new Observable will be the result of the function applied to the first item emitted by
     * all of the Observalbes; the second item emitted by the new Observable will be the result of
     * the function applied to the second item emitted by each of those Observables; and so forth.
     * <p>
     * The resulting {@code Observable<R>} returned from {@code zip} will invoke {@code onNext} as many times as the number of {@code onNext} invokations of the
     * source Observable that emits the fewest items.
     * <p>
     * <img width="640" src="https://raw.github.com/wiki/Netflix/RxJava/images/rx-operators/zip.png">
     * 
     * @param ws
     *            A collection of source Observables
     * @param reduceFunction
     *            a function that, when applied to an item emitted by each of the source
     *            Observables, results in an item that will be emitted by the resulting Observable
     * @return an Observable that emits the zipped results
     */
    public static <R> Observable<R> zip(Collection<Observable<?>> ws, FuncN<R> reduceFunction) {
        return create(OperationZip.zip(ws, reduceFunction));
    }

    /**
     * <p>
     * <img width="640" src="https://raw.github.com/wiki/Netflix/RxJava/images/rx-operators/filter.png">
     * 
     * @param predicate
     *            a function that evaluates the items emitted by the source Observable, returning {@code true} if they pass the filter
     * @return an Observable that emits only those items in the original Observable that the filter
     *         evaluates as {@code true}
     */
    public Observable<T> filter(Func1<T, Boolean> predicate) {
        return create(OperationFilter.filter(this, predicate));
    }

    /**
     * Registers an {@link Action0} to be called when this Observable invokes {@link Observer#onCompleted onCompleted} or {@link Observer#onError onError}.
     * <p>
     * <img width="640" src="https://github.com/Netflix/RxJava/wiki/images/rx-operators/finallyDo.png">
     * 
     * @param action
     *            an {@link Action0} to be invoked when the source Observable finishes
     * @return an Observable that emits the same items as the source Observable, then invokes the {@link Action0}
     * @see <a href="http://msdn.microsoft.com/en-us/library/hh212133(v=vs.103).aspx">MSDN: Observable.Finally Method</a>
     */
    public Observable<T> finallyDo(Action0 action) {
        return create(OperationFinally.finallyDo(this, action));
    }

    /**
     * Creates a new Observable by applying a function that you supply to each item emitted by
     * the source Observable, where that function returns an Observable, and then merging those
     * resulting Observables and emitting the results of this merger.
     * <p>
     * <img width="640" src="https://raw.github.com/wiki/Netflix/RxJava/images/rx-operators/flatMap.png">
     * <p>
     * Note: {@code mapMany} and {@code flatMap} are equivalent.
     * 
     * @param func
     *            a function that, when applied to an item emitted by the source Observable, returns
     *            an Observable
     * @return an Observable that emits the result of applying the transformation function to each
     *         item emitted by the source Observable and merging the results of the Observables
     *         obtained from this transformation.
     * @see #mapMany(Func1)
     */
    public <R> Observable<R> flatMap(Func1<T, Observable<R>> func) {
        return mapMany(func);
    }

    /**
     * <p>
     * <img width="640" src="https://raw.github.com/wiki/Netflix/RxJava/images/rx-operators/where.png">
     * 
     * @param predicate
     *            a function that evaluates an item emitted by the source Observable, returning {@code true} if it passes the filter
     * @return an Observable that emits only those items in the original Observable that the filter
     *         evaluates as {@code true}
     * @see #filter(Func1)
     */
    public Observable<T> where(Func1<T, Boolean> predicate) {
        return filter(predicate);
    }

    /**
     * Returns an Observable that applies the given function to each item emitted by an
     * Observable and emits the result.
     * <p>
     * <img width="640" src="https://raw.github.com/wiki/Netflix/RxJava/images/rx-operators/map.png">
     * 
     * @param func
     *            a function to apply to each item emitted by the Observable
     * @return an Observable that emits the items from the source Observable, transformed by the
     *         given function
     */
    public <R> Observable<R> map(Func1<T, R> func) {
        return create(OperationMap.map(this, func));
    }

    /**
     * Creates a new Observable by applying a function that you supply to each item emitted by
     * the source Observable, where that function returns an Observable, and then merging those
     * resulting Observables and emitting the results of this merger.
     * <p>
     * <img width="640" src="https://raw.github.com/wiki/Netflix/RxJava/images/rx-operators/mapMany.png">
     * <p>
     * Note: <code>mapMany</code> and <code>flatMap</code> are equivalent.
     * 
     * @param func
     *            a function that, when applied to an item emitted by the source Observable, returns
     *            an Observable
     * @return an Observable that emits the result of applying the transformation function to each
     *         item emitted by the source Observable and merging the results of the Observables
     *         obtained from this transformation.
     * @see #flatMap(Func1)
     */
    public <R> Observable<R> mapMany(Func1<T, Observable<R>> func) {
        return create(OperationMap.mapMany(this, func));
    }

    /**
     * Turns all of the notifications from a source Observable into {@link Observer#onNext onNext} emissions, and marks them with their original notification types within {@link Notification} objects.
     * <p>
     * <img width="640" src="https://raw.github.com/wiki/Netflix/RxJava/images/rx-operators/materialize.png">
     * 
     * @return an Observable whose items are the result of materializing the items and
     *         notifications of the source Observable
     * @see <a href="http://msdn.microsoft.com/en-us/library/hh229453(v=VS.103).aspx">MSDN: Observable.materialize</a>
     */
    public Observable<Notification<T>> materialize() {
        return create(OperationMaterialize.materialize(this));
    }

    /**
     * Asynchronously subscribes and unsubscribes Observers on the specified {@link Scheduler}.
     * <p>
     * <img width="640" src="https://github.com/Netflix/RxJava/wiki/images/rx-operators/subscribeOn.png">
     * 
     * @param scheduler
     *            the {@link Scheduler} to perform subscription and unsubscription actions on
     * @return the source Observable modified so that its subscriptions and unsubscriptions happen
     *         on the specified {@link Scheduler}
     */
    public Observable<T> subscribeOn(Scheduler scheduler) {
        return create(OperationSubscribeOn.subscribeOn(this, scheduler));
    }

    /**
     * Asynchronously notify {@link Observer}s on the specified {@link Scheduler}.
     * <p>
     * <img width="640" src="https://github.com/Netflix/RxJava/wiki/images/rx-operators/observeOn.png">
     * 
     * @param scheduler
     *            the {@link Scheduler} to notify {@link Observer}s on
     * @return the source Observable modified so that its {@link Observer}s are notified on the
     *         specified {@link Scheduler}
     */
    public Observable<T> observeOn(Scheduler scheduler) {
        return create(OperationObserveOn.observeOn(this, scheduler));
    }

    /**
     * Returns an Observable that reverses the effect of {@link #materialize materialize} by
     * transforming the {@link Notification} objects emitted by the source Observable into the items
     * or notifications they represent.
     * <p>
     * <img width="640" src="https://github.com/Netflix/RxJava/wiki/images/rx-operators/dematerialize.png">
     * 
     * @return an Observable that emits the items and notifications embedded in the {@link Notification} objects emitted by the source Observable
     * @see <a href="http://msdn.microsoft.com/en-us/library/hh229047(v=vs.103).aspx">MSDN: Observable.dematerialize</a>
     * @throws Throwable
     *             if the source Observable is not of type {@code Observable<Notification<T>>}.
     */
    @SuppressWarnings("unchecked")
    public <T2> Observable<T2> dematerialize() {
        return create(OperationDematerialize.dematerialize((Observable<Notification<T2>>) this));
    }

    /**
     * Instruct an Observable to pass control to another Observable rather than invoking {@link Observer#onError onError} if it encounters an error.
     * <p>
     * <img width="640" src="https://raw.github.com/wiki/Netflix/RxJava/images/rx-operators/onErrorResumeNext.png">
     * <p>
     * By default, when an Observable encounters an error that prevents it from emitting the
     * expected item to its {@link Observer}, the Observable invokes its Observer's
     * <code>onError</code> method, and then quits without invoking any more of its Observer's
     * methods. The <code>onErrorResumeNext</code> method changes this behavior. If you pass a
     * function that returns an Observable (<code>resumeFunction</code>) to
     * <code>onErrorResumeNext</code>, if the original Observable encounters an error, instead of
     * invoking its Observer's <code>onError</code> method, it will instead relinquish control to
     * the Observable returned from <code>resumeFunction</code>, which will invoke the Observer's {@link Observer#onNext onNext} method if it is able to do so. In such a case, because no
     * Observable necessarily invokes <code>onError</code>, the Observer may never know that an
     * error happened.
     * <p>
     * You can use this to prevent errors from propagating or to supply fallback data should errors
     * be encountered.
     * 
     * @param resumeFunction
     *            a function that returns an Observable that will take over if the source Observable
     *            encounters an error
     * @return the original Observable, with appropriately modified behavior
     */
    public Observable<T> onErrorResumeNext(final Func1<Throwable, Observable<T>> resumeFunction) {
        return create(OperationOnErrorResumeNextViaFunction.onErrorResumeNextViaFunction(this, resumeFunction));
    }

    /**
     * Instruct an Observable to pass control to another Observable rather than invoking {@link Observer#onError onError} if it encounters an error.
     * <p>
     * <img width="640" src="https://raw.github.com/wiki/Netflix/RxJava/images/rx-operators/onErrorResumeNext.png">
     * <p>
     * By default, when an Observable encounters an error that prevents it from emitting the
     * expected item to its {@link Observer}, the Observable invokes its Observer's
     * <code>onError</code> method, and then quits without invoking any more of its Observer's
     * methods. The <code>onErrorResumeNext</code> method changes this behavior. If you pass
     * another Observable (<code>resumeSequence</code>) to an Observable's
     * <code>onErrorResumeNext</code> method, if the original Observable encounters an error,
     * instead of invoking its Observer's <code>onError</code> method, it will instead relinquish
     * control to <code>resumeSequence</code> which will invoke the Observer's {@link Observer#onNext onNext} method if it is able to do so. In such a case, because no
     * Observable necessarily invokes <code>onError</code>, the Observer may never know that an
     * error happened.
     * <p>
     * You can use this to prevent errors from propagating or to supply fallback data should errors
     * be encountered.
     * 
     * @param resumeSequence
     *            a function that returns an Observable that will take over if the source Observable
     *            encounters an error
     * @return the original Observable, with appropriately modified behavior
     */
    public Observable<T> onErrorResumeNext(final Observable<T> resumeSequence) {
        return create(OperationOnErrorResumeNextViaObservable.onErrorResumeNextViaObservable(this, resumeSequence));
    }

    /**
     * Instruct an Observable to pass control to another Observable rather than invoking {@link Observer#onError onError} if it encounters an error of type {@link java.lang.Exception}.
     * <p>
     * This differs from {@link #onErrorResumeNext} in that this one does not handle {@link java.lang.Throwable} or {@link java.lang.Error} but lets those continue through.
     * <p>
     * <img width="640" src="https://raw.github.com/wiki/Netflix/RxJava/images/rx-operators/onErrorResumeNext.png">
     * <p>
     * By default, when an Observable encounters an error that prevents it from emitting the
     * expected item to its {@link Observer}, the Observable invokes its Observer's
     * <code>onError</code> method, and then quits without invoking any more of its Observer's
     * methods. The <code>onErrorResumeNext</code> method changes this behavior. If you pass
     * another Observable (<code>resumeSequence</code>) to an Observable's
     * <code>onErrorResumeNext</code> method, if the original Observable encounters an error,
     * instead of invoking its Observer's <code>onError</code> method, it will instead relinquish
     * control to <code>resumeSequence</code> which will invoke the Observer's {@link Observer#onNext onNext} method if it is able to do so. In such a case, because no
     * Observable necessarily invokes <code>onError</code>, the Observer may never know that an
     * error happened.
     * <p>
     * You can use this to prevent errors from propagating or to supply fallback data should errors
     * be encountered.
     * 
     * @param resumeSequence
     *            a function that returns an Observable that will take over if the source Observable
     *            encounters an error
     * @return the original Observable, with appropriately modified behavior
     */
    public Observable<T> onExceptionResumeNext(final Observable<T> resumeSequence) {
        return create(OperationOnExceptionResumeNextViaObservable.onExceptionResumeNextViaObservable(this, resumeSequence));
    }

    /**
     * Instruct an Observable to emit an item (returned by a specified function) rather than
     * invoking {@link Observer#onError onError} if it encounters an error.
     * <p>
     * <img width="640" src="https://github.com/Netflix/RxJava/wiki/images/rx-operators/onErrorReturn.png">
     * <p>
     * By default, when an Observable encounters an error that prevents it from emitting the
     * expected item to its {@link Observer}, the Observable invokes its Observer's
     * <code>onError</code> method, and then quits without invoking any more of its Observer's
     * methods. The <code>onErrorReturn</code> method changes this behavior. If you pass a function
     * (<code>resumeFunction</code>) to an Observable's <code>onErrorReturn</code> method, if the
     * original Observable encounters an error, instead of invoking its Observer's
     * <code>onError</code> method, it will instead pass the return value of
     * <code>resumeFunction</code> to the Observer's {@link Observer#onNext onNext} method.
     * <p>
     * You can use this to prevent errors from propagating or to supply fallback data should errors
     * be encountered.
     * 
     * @param resumeFunction
     *            a function that returns an item that the new Observable will emit if the source
     *            Observable encounters an error
     * @return the original Observable with appropriately modified behavior
     */
    public Observable<T> onErrorReturn(Func1<Throwable, T> resumeFunction) {
        return create(OperationOnErrorReturn.onErrorReturn(this, resumeFunction));
    }

    /**
     * Returns an Observable that applies a function of your choosing to the first item emitted by a
     * source Observable, then feeds the result of that function along with the second item emitted
     * by the source Observable into the same function, and so on until all items have been emitted
     * by the source Observable, and emits the final result from the final call to your function as
     * its sole item.
     * <p>
     * <img width="640" src="https://raw.github.com/wiki/Netflix/RxJava/images/rx-operators/reduce.png">
     * <p>
     * This technique, which is called "reduce" or "aggregate" here, is sometimes called "fold,"
     * "accumulate," "compress," or "inject" in other programming contexts. Groovy, for instance,
     * has an <code>inject</code> method that does a similar operation on lists.
     * 
     * @param accumulator
     *            An accumulator function to be invoked on each item emitted by the source
     *            Observable, whose result will be used in the next accumulator call
     * @return an Observable that emits a single item that is the result of accumulating the
     *         output from the source Observable
     * @see <a href="http://msdn.microsoft.com/en-us/library/hh229154(v%3Dvs.103).aspx">MSDN: Observable.Aggregate</a>
     * @see <a href="http://en.wikipedia.org/wiki/Fold_(higher-order_function)">Wikipedia: Fold (higher-order function)</a>
     */
    public Observable<T> reduce(Func2<T, T, T> accumulator) {
        return create(OperationScan.scan(this, accumulator)).takeLast(1);
    }

    /**
     * Returns a {@link ConnectableObservable} that shares a single subscription to the underlying
     * Observable that will replay all of its items and notifications to any future {@link Observer}.
     * <p>
     * <img width="640" src="https://raw.github.com/wiki/Netflix/RxJava/images/rx-operators/replay.png">
     * 
     * @return a {@link ConnectableObservable} that upon connection causes the source Observable to
     *         emit items to its {@link Observer}s
     */
    public ConnectableObservable<T> replay() {
        return OperationMulticast.multicast(this, ReplaySubject.<T> create());
    }

    /**
     * This method has similar behavior to {@link #replay} except that this auto-subscribes to
     * the source Observable rather than returning a {@link ConnectableObservable}.
     * <p>
     * <img width="640" src="https://github.com/Netflix/RxJava/wiki/images/rx-operators/cache.png">
     * <p>
     * This is useful when you want an Observable to cache responses and you can't control the
     * subscribe/unsubscribe behavior of all the {@link Observer}s.
     * <p>
     * NOTE: You sacrifice the ability to unsubscribe from the origin when you use the
     * <code>cache()</code> operator so be careful not to use this operator on Observables that
     * emit an infinite or very large number of items that will use up memory.
     * 
     * @return an Observable that when first subscribed to, caches all of its notifications for
     *         the benefit of subsequent subscribers.
     */
    public Observable<T> cache() {
        return create(OperationCache.cache(this));
    }

    /**
     * Returns a {@link ConnectableObservable}, which waits until its {@link ConnectableObservable#connect connect} method is called before it begins emitting
     * items to those {@link Observer}s that have subscribed to it.
     * <p>
     * <img width="640" src="https://github.com/Netflix/RxJava/wiki/images/rx-operators/publishConnect.png">
     * 
     * @return a {@link ConnectableObservable} that upon connection causes the source Observable to
     *         emit items to its {@link Observer}s
     */
    public ConnectableObservable<T> publish() {
        return OperationMulticast.multicast(this, PublishSubject.<T> create());
    }

    /**
     * Synonymous with <code>reduce()</code>.
     * <p>
     * <img width="640" src="https://raw.github.com/wiki/Netflix/RxJava/images/rx-operators/aggregate.png">
     * 
     * @see #reduce(Func2)
     */
    public Observable<T> aggregate(Func2<T, T, T> accumulator) {
        return reduce(accumulator);
    }

    /**
     * Returns an Observable that applies a function of your choosing to the first item emitted by a
     * source Observable, then feeds the result of that function along with the second item emitted
     * by an Observable into the same function, and so on until all items have been emitted by the
     * source Observable, emitting the final result from the final call to your function as its sole
     * item.
     * <p>
     * <img width="640" src="https://raw.github.com/wiki/Netflix/RxJava/images/rx-operators/reduceSeed.png">
     * <p>
     * This technique, which is called "reduce" or "aggregate" here, is sometimes called "fold,"
     * "accumulate," "compress," or "inject" in other programming contexts. Groovy, for instance,
     * has an <code>inject</code> method that does a similar operation on lists.
     * 
     * @param initialValue
     *            the initial (seed) accumulator value
     * @param accumulator
     *            an accumulator function to be invoked on each item emitted by the source
     *            Observable, the result of which will be used in the next accumulator call
     * @return an Observable that emits a single item that is the result of accumulating the output
     *         from the items emitted by the source Observable
     * @see <a href="http://msdn.microsoft.com/en-us/library/hh229154(v%3Dvs.103).aspx">MSDN: Observable.Aggregate</a>
     * @see <a href="http://en.wikipedia.org/wiki/Fold_(higher-order_function)">Wikipedia: Fold (higher-order function)</a>
     */
    public <R> Observable<R> reduce(R initialValue, Func2<R, T, R> accumulator) {
        return create(OperationScan.scan(this, initialValue, accumulator)).takeLast(1);
    }

    /**
     * Synonymous with <code>reduce()</code>.
     * <p>
     * <img width="640" src="https://raw.github.com/wiki/Netflix/RxJava/images/rx-operators/aggregateSeed.png">
     * 
     * @see #reduce(Object, Func2)
     */
    public <R> Observable<R> aggregate(R initialValue, Func2<R, T, R> accumulator) {
        return reduce(initialValue, accumulator);
    }

    /**
     * Returns an Observable that applies a function of your choosing to the first item emitted by a
     * source Observable, then feeds the result of that function along with the second item emitted
     * by an Observable into the same function, and so on until all items have been emitted by the
     * source Observable, emitting the result of each of these iterations.
     * <p>
     * <img width="640" src="https://raw.github.com/wiki/Netflix/RxJava/images/rx-operators/scan.png">
     * <p>
     * This sort of function is sometimes called an accumulator.
     * <p>
     * Note that when you pass a seed to <code>scan()</code> the resulting Observable will emit
     * that seed as its first emitted item.
     * 
     * @param accumulator
     *            an accumulator function to be invoked on each item emitted by the source
     *            Observable, whose result will be emitted to {@link Observer}s via {@link Observer#onNext onNext} and used in the next accumulator call.
     * @return an Observable that emits the results of each call to the accumulator function
     * @see <a href="http://msdn.microsoft.com/en-us/library/hh211665(v%3Dvs.103).aspx">MSDN: Observable.Scan</a>
     */
    public Observable<T> scan(Func2<T, T, T> accumulator) {
        return create(OperationScan.scan(this, accumulator));
    }

    /**
     * Returns an Observable that emits the results of sampling the items emitted by the source
     * Observable at a specified time interval.
     * <p>
     * <img width="640" src="https://github.com/Netflix/RxJava/wiki/images/rx-operators/sample.png">
     * 
     * @param period
     *            the sampling rate
     * @param unit
     *            the {@link TimeUnit} in which <code>period</code> is defined
     * @return an Observable that emits the results of sampling the items emitted by the source
     *         Observable at the specified time interval
     */
    public Observable<T> sample(long period, TimeUnit unit) {
        return create(OperationSample.sample(this, period, unit));
    }

    /**
     * Returns an Observable that emits the results of sampling the items emitted by the source
     * Observable at a specified time interval.
     * <p>
     * <img width="640" src="https://github.com/Netflix/RxJava/wiki/images/rx-operators/sample.png">
     * 
     * @param period
     *            the sampling rate
     * @param unit
     *            the {@link TimeUnit} in which <code>period</code> is defined
     * @param scheduler
     *            the {@link Scheduler} to use when sampling
     * @return an Observable that emits the results of sampling the items emitted by the source
     *         Observable at the specified time interval
     */
    public Observable<T> sample(long period, TimeUnit unit, Scheduler scheduler) {
        return create(OperationSample.sample(this, period, unit, scheduler));
    }

    /**
     * Returns an Observable that applies a function of your choosing to the first item emitted by a
     * source Observable, then feeds the result of that function along with the second item emitted
     * by an Observable into the same function, and so on until all items have been emitted by the
     * source Observable, emitting the result of each of these iterations.
     * <p>
     * <img width="640" src="https://raw.github.com/wiki/Netflix/RxJava/images/rx-operators/scanSeed.png">
     * <p>
     * This sort of function is sometimes called an accumulator.
     * <p>
     * Note that when you pass a seed to <code>scan()</code> the resulting Observable will emit
     * that seed as its first emitted item.
     * 
     * @param initialValue
     *            the initial (seed) accumulator value
     * @param accumulator
     *            an accumulator function to be invoked on each item emitted by the source
     *            Observable, whose result will be emitted to {@link Observer}s via {@link Observer#onNext onNext} and used in the next accumulator call.
     * @return an Observable that emits the results of each call to the accumulator function
     * @see <a href="http://msdn.microsoft.com/en-us/library/hh211665(v%3Dvs.103).aspx">MSDN: Observable.Scan</a>
     */
    public <R> Observable<R> scan(R initialValue, Func2<R, T, R> accumulator) {
        return create(OperationScan.scan(this, initialValue, accumulator));
    }

    /**
     * Returns an Observable that emits a Boolean that indicates whether all of the items emitted by
     * the source Observable satisfy a condition.
     * <p>
     * <img width="640" src="https://github.com/Netflix/RxJava/wiki/images/rx-operators/all.png">
     * 
     * @param predicate
     *            a function that evaluates an item and returns a Boolean
     * @return an Observable that emits <code>true</code> if all items emitted by the source
     *         Observable satisfy the predicate; otherwise, <code>false</code>
     */
    public Observable<Boolean> all(Func1<T, Boolean> predicate) {
        return create(OperationAll.all(this, predicate));
    }

    /**
     * Returns an Observable that skips the first <code>num</code> items emitted by the source
     * Observable and emits the remainder.
     * <p>
     * <img width="640" src="https://raw.github.com/wiki/Netflix/RxJava/images/rx-operators/skip.png">
     * <p>
     * You can ignore the first <code>num</code> items emitted by an Observable and attend only to
     * those items that come after, by modifying the Observable with the <code>skip</code> method.
     * 
     * @param num
     *            the number of items to skip
     * @return an Observable that is identical to the source Observable except that it does not
     *         emit the first <code>num</code> items that the source emits
     */
    public Observable<T> skip(int num) {
        return create(OperationSkip.skip(this, num));
    }

    /**
     * Returns an Observable that emits only the first <code>num</code> items emitted by the source
     * Observable.
     * <p>
     * <img width="640" src="https://raw.github.com/wiki/Netflix/RxJava/images/rx-operators/take.png">
     * <p>
     * This method returns an Observable that will invoke a subscribing {@link Observer}'s {@link Observer#onNext onNext} function a maximum of <code>num</code> times before invoking
     * {@link Observer#onCompleted onCompleted}.
     * 
     * @param num
     *            the number of items to take
     * @return an Observable that emits only the first <code>num</code> items from the source
     *         Observable, or all of the items from the source Observable if that Observable emits
     *         fewer than <code>num</code> items
     */
    public Observable<T> take(final int num) {
        return create(OperationTake.take(this, num));
    }

    /**
     * Returns an Observable that emits items emitted by the source Observable so long as a
     * specified condition is true.
     * <p>
     * <img width="640" src="https://github.com/Netflix/RxJava/wiki/images/rx-operators/takeWhile.png">
     * 
     * @param predicate
     *            a function that evaluates an item emitted by the source Observable and returns a
     *            Boolean
     * @return an Observable that emits the items from the source Observable so long as each item
     *         satisfies the condition defined by <code>predicate</code>
     */
    public Observable<T> takeWhile(final Func1<T, Boolean> predicate) {
        return create(OperationTakeWhile.takeWhile(this, predicate));
    }

    /**
     * Returns an Observable that emits the items emitted by a source Observable so long as a given
     * predicate remains true, where the predicate can operate on both the item and its index
     * relative to the complete sequence.
     * <p>
     * <img width="640" src="https://github.com/Netflix/RxJava/wiki/images/rx-operators/takeWhileWithIndex.png">
     * 
     * @param predicate
     *            a function to test each item emitted by the source Observable for a condition;
     *            the second parameter of the function represents the index of the source item
     * @return an Observable that emits items from the source Observable so long as the predicate
     *         continues to return <code>true</code> for each item, then completes
     */
    public Observable<T> takeWhileWithIndex(final Func2<T, Integer, Boolean> predicate) {
        return create(OperationTakeWhile.takeWhileWithIndex(this, predicate));
    }

    /**
     * Returns an Observable that emits only the last <code>count</code> items emitted by the source
     * Observable.
     * <p>
     * <img width="640" src="https://github.com/Netflix/RxJava/wiki/images/rx-operators/last.png">
     * 
     * @param count
     *            the number of items to emit from the end of the sequence emitted by the source
     *            Observable
     * @return an Observable that emits only the last <code>count</code> items emitted by the source
     *         Observable
     */
    public Observable<T> takeLast(final int count) {
        return create(OperationTakeLast.takeLast(this, count));
    }

    /**
     * Returns an Observable that emits the items from the source Observable only until the
     * <code>other</code> Observable emits an item.
     * <p>
     * <img width="640" src="https://github.com/Netflix/RxJava/wiki/images/rx-operators/takeUntil.png">
     * 
     * @param other
     *            the Observable whose first emitted item will cause <code>takeUntil</code> to stop
     *            emitting items from the source Observable
     * @param <E>
     *            the type of items emitted by <code>other</code>
     * @return an Observable that emits the items of the source Observable until such time as
     *         <code>other</code> emits its first item
     */
    public <E> Observable<T> takeUntil(Observable<E> other) {
        return OperationTakeUntil.takeUntil(this, other);
    }

    /**
     * Returns an Observable that emits a single item, a list composed of all the items emitted by
     * the source Observable.
     * <p>
     * <img width="640" src="https://raw.github.com/wiki/Netflix/RxJava/images/rx-operators/toList.png">
     * <p>
     * Normally, an Observable that returns multiple items will do so by invoking its {@link Observer}'s {@link Observer#onNext onNext} method for each such item. You can change
     * this behavior, instructing the Observable to compose a list of all of these items and then to
     * invoke the Observer's <code>onNext</code> function once, passing it the entire list, by
     * calling the Observable's <code>toList</code> method prior to calling its {@link #subscribe} method.
     * <p>
     * Be careful not to use this operator on Observables that emit infinite or very large numbers
     * of items, as you do not have the option to unsubscribe.
     * 
     * @return an Observable that emits a single item: a List containing all of the items emitted by
     *         the source Observable.
     */
    public Observable<List<T>> toList() {
        return create(OperationToObservableList.toObservableList(this));
    }

    /**
     * Return an Observable that emits the items emitted by the source Observable, in a sorted
     * order (each item emitted by the Observable must implement {@link Comparable} with respect to
     * all other items in the sequence).
     * <p>
     * <img width="640" src="https://raw.github.com/wiki/Netflix/RxJava/images/rx-operators/toSortedList.png">
     * 
     * @throws ClassCastException
     *             if any item emitted by the Observable does not implement {@link Comparable} with
     *             respect to all other items emitted by the Observable
     * @return an Observable that emits the items from the source Observable in sorted order
     */
    public Observable<List<T>> toSortedList() {
        return create(OperationToObservableSortedList.toSortedList(this));
    }

    /**
     * Return an Observable that emits the items emitted by the source Observable, in a sorted
     * order based on a specified comparison function
     * <p>
     * <img width="640" src="https://raw.github.com/wiki/Netflix/RxJava/images/rx-operators/toSortedList.f.png">
     * 
     * @param sortFunction
     *            a function that compares two items emitted by the source Observable and returns
     *            an Integer that indicates their sort order
     * @return an Observable that emits the items from the source Observable in sorted order
     */
    public Observable<List<T>> toSortedList(Func2<T, T, Integer> sortFunction) {
        return create(OperationToObservableSortedList.toSortedList(this, sortFunction));
    }

    /**
     * Emit a specified set of items before beginning to emit items from the source Observable.
     * <p>
     * <img width="640" src="https://raw.github.com/wiki/Netflix/RxJava/images/rx-operators/startWith.png">
     * 
     * @param values
     *            the items you want the modified Observable to emit first
     * @return an Observable that exhibits the modified behavior
     */
    @SuppressWarnings("unchecked")
    public Observable<T> startWith(T... values) {
        return concat(Observable.<T> from(values), this);
    }

    /**
     * Groups the items emitted by an Observable according to a specified criterion, and emits these
     * grouped items as {@link GroupedObservable}s, one GroupedObservable per group.
     * <p>
     * <img width="640" src="https://github.com/Netflix/RxJava/wiki/images/rx-operators/groupBy.png">
     * 
     * @param keySelector
     *            a function that extracts the key from an item
     * @param elementSelector
     *            a function to map a source item to an item in a {@link GroupedObservable}
     * @param <K>
     *            the key type
     * @param <R>
     *            the type of items emitted by the resulting {@link GroupedObservable}s
     * @return an Observable that emits {@link GroupedObservable}s, each of which corresponds to a
     *         unique key value and emits items representing items from the source Observable that
     *         share that key value
     */
    public <K, R> Observable<GroupedObservable<K, R>> groupBy(final Func1<T, K> keySelector, final Func1<T, R> elementSelector) {
        return create(OperationGroupBy.groupBy(this, keySelector, elementSelector));
    }

    /**
     * Groups the items emitted by an Observable according to a specified criterion, and emits these
     * grouped items as {@link GroupedObservable}s, one GroupedObservable per group.
     * <p>
     * <img width="640" src="https://github.com/Netflix/RxJava/wiki/images/rx-operators/groupBy.png">
     * 
     * @param keySelector
     *            a function that extracts the key for each item
     * @param <K>
     *            the key type
     * @return an Observable that emits {@link GroupedObservable}s, each of which corresponds to a
     *         unique key value and emits items representing items from the source Observable that
     *         share that key value
     */
    public <K> Observable<GroupedObservable<K, T>> groupBy(final Func1<T, K> keySelector) {
        return create(OperationGroupBy.groupBy(this, keySelector));
    }

    /**
     * Converts an Observable into a {@link BlockingObservable} (an Observable with blocking
     * operators).
     * 
     * @see <a href="https://github.com/Netflix/RxJava/wiki/Blocking-Observable-Operators">Blocking Observable Operators</a>
     */
    public BlockingObservable<T> toBlockingObservable() {
        return BlockingObservable.from(this);
    }

    /**
     * Whether a given {@link Function} is an internal implementation inside rx.* packages or not.
     * <p>
     * For why this is being used see https://github.com/Netflix/RxJava/issues/216 for discussion on "Guideline 6.4: Protect calls to user code from within an operator"
     * 
     * NOTE: If strong reasons for not depending on package names comes up then the implementation of this method can change to looking for a marker interface.
     * 
     * @param f
     * @return {@code true} if the given function is an internal implementation, and {@code false} otherwise.
     */
    private boolean isInternalImplementation(Object o) {
        if (o == null) {
            return true;
        }
        // prevent double-wrapping (yeah it happens)
        if (o instanceof SafeObserver)
            return true;
        // we treat the following package as "internal" and don't wrap it
        Package p = o.getClass().getPackage(); // it can be null
        return p != null && p.getName().startsWith("rx.operators");
    }

}
