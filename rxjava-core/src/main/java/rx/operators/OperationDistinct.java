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
package rx.operators;

import static org.mockito.Matchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.initMocks;
import static rx.Observable.create;
import static rx.Observable.empty;
import static rx.Observable.from;

import java.util.HashSet;
import java.util.Set;

import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;
import org.mockito.Mock;

import rx.Observable;
import rx.Observable.OnSubscribeFunc;
import rx.Observer;
import rx.Subscription;
import rx.subscriptions.Subscriptions;
import rx.util.functions.Action0;
import rx.util.functions.Func1;
import rx.util.functions.Functions;

/**
 * Returns an Observable that emits all distinct items emitted by the source.
 * 
 * Be careful with this operation when using infinite or very large observables 
 * as it has to store all distinct values it has received.
 */
public final class OperationDistinct {

    /**
     * Returns an Observable that emits all distinct items emitted by the source
     * @param source
     *            The source Observable to emit the distinct items for.
     * @return A subscription function for creating the target Observable.
     */
    public static <T, U> OnSubscribeFunc<T> distinct(Observable<? extends T> source, Func1<? super T, ? extends U> keySelector) {
        return new Distinct<T, U>(source, keySelector);
    }
    
    /**
     * Returns an Observable that emits all distinct items emitted by the source
     * @param source
     *            The source Observable to emit the distinct items for.
     * @return A subscription function for creating the target Observable.
     */
    public static <T> OnSubscribeFunc<T> distinct(Observable<? extends T> source) {
        return new Distinct<T, T>(source, Functions.<T>identity());
    }
    
    private static class Distinct<T, U> implements OnSubscribeFunc<T> {
        private final Observable<? extends T> source;
        private final Func1<? super T, ? extends U> keySelector;
        
        private Distinct(Observable<? extends T> source, Func1<? super T, ? extends U> keySelector) {
            this.source = source;
            this.keySelector = keySelector;
        }

        @Override
        public Subscription onSubscribe(final Observer<? super T> observer) {
            final Subscription sourceSub = source.subscribe(new Observer<T>() {
                private final Set<U> emittedKeys = new HashSet<U>();
                
                @Override
                public void onCompleted() {
                    observer.onCompleted();
                }

                @Override
                public void onError(Throwable e) {
                    observer.onError(e);
                }

                @Override
                public void onNext(T next) {
                    try {
                        U nextKey = keySelector.call(next);
                        if (!emittedKeys.contains(nextKey)) {
                            emittedKeys.add(nextKey);
                            observer.onNext(next);
                        }
                    } catch (Throwable t) {
                        // keySelector is a user function, may throw something
                        observer.onError(t);
                    }
                }
            });
            
            return Subscriptions.create(new Action0() {
                @Override
                public void call() {
                    sourceSub.unsubscribe();
                }
            });
        }
    }
    
    public static class UnitTest {
        @Mock
        Observer<? super String> w;
        
        // nulls lead to exceptions
        final Func1<String, String> TO_UPPER_WITH_EXCEPTION = new Func1<String, String>() {
            @Override
            public String call(String s) {
                return s.toUpperCase();
            }
        };
        
        @Before
        public void before() {
            initMocks(this);
        }

        @Test
        public void testDistinctOfNone() {
            Observable<String> src = empty();
            create(distinct(src)).subscribe(w);

            verify(w, never()).onNext(anyString());
            verify(w, never()).onError(any(Throwable.class));
            verify(w, times(1)).onCompleted();
        }

        @Test
        public void testDistinctOfNoneWithKeySelector() {
            Observable<String> src = empty();
            create(distinct(src, TO_UPPER_WITH_EXCEPTION)).subscribe(w);

            verify(w, never()).onNext(anyString());
            verify(w, never()).onError(any(Throwable.class));
            verify(w, times(1)).onCompleted();
        }

        @Test
        public void testDistinctOfNormalSource() {
            Observable<String> src = from("a", "b", "c", "c", "c", "b", "b", "a", "e");
            create(distinct(src)).subscribe(w);

            InOrder inOrder = inOrder(w); 
            inOrder.verify(w, times(1)).onNext("a");
            inOrder.verify(w, times(1)).onNext("b");
            inOrder.verify(w, times(1)).onNext("c");
            inOrder.verify(w, times(1)).onNext("e");
            inOrder.verify(w, times(1)).onCompleted();
            inOrder.verify(w, never()).onNext(anyString());
            verify(w, never()).onError(any(Throwable.class));
        }

        @Test
        public void testDistinctOfNormalSourceWithKeySelector() {
            Observable<String> src = from("a", "B", "c", "C", "c", "B", "b", "a", "E");
            create(distinct(src, TO_UPPER_WITH_EXCEPTION)).subscribe(w);

            InOrder inOrder = inOrder(w); 
            inOrder.verify(w, times(1)).onNext("a");
            inOrder.verify(w, times(1)).onNext("B");
            inOrder.verify(w, times(1)).onNext("c");
            inOrder.verify(w, times(1)).onNext("E");
            inOrder.verify(w, times(1)).onCompleted();
            inOrder.verify(w, never()).onNext(anyString());
            verify(w, never()).onError(any(Throwable.class));
        }

        @Test
        public void testDistinctOfSourceWithNulls() {
            Observable<String> src = from(null, "a", "a", null, null, "b", null);
            create(distinct(src)).subscribe(w);

            InOrder inOrder = inOrder(w); 
            inOrder.verify(w, times(1)).onNext(null);
            inOrder.verify(w, times(1)).onNext("a");
            inOrder.verify(w, times(1)).onNext("b");
            inOrder.verify(w, times(1)).onCompleted();
            inOrder.verify(w, never()).onNext(anyString());
            verify(w, never()).onError(any(Throwable.class));
        }

        @Test
        public void testDistinctOfSourceWithExceptionsFromKeySelector() {
            Observable<String> src = from("a", "b", null, "c");
            create(distinct(src, TO_UPPER_WITH_EXCEPTION)).subscribe(w);

            InOrder inOrder = inOrder(w); 
            inOrder.verify(w, times(1)).onNext("a");
            inOrder.verify(w, times(1)).onNext("b");
            inOrder.verify(w, times(1)).onError(any(NullPointerException.class));
            inOrder.verify(w, never()).onNext(anyString());
            inOrder.verify(w, never()).onCompleted();
        }
    }
}
