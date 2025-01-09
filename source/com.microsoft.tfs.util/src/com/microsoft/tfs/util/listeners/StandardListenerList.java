package com.microsoft.tfs.util.listeners;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.microsoft.tfs.util.Check;

public class StandardListenerList implements ListenerList {
    private final Comparator comparator;
    private final Object modifyLock = new Object();
    private volatile ListenerNode listeners;

    public StandardListenerList() {
        this(Comparators.IDENTITY);
    }

    public StandardListenerList(final Comparator listenerComparator) {
        Check.notNull(listenerComparator, "listenerComparator");
        comparator = listenerComparator;
    }

    @Override
    public boolean addListener(final Object listener) {
        Check.notNull(listener, "listener");

        synchronized (modifyLock) {
            if (listeners == null) {
                listeners = new ListenerNode(listener, comparator, null);
                return true;
            }

            final ListenerNodeHolder holder = new ListenerNodeHolder();
            if (addListenerIterative(listener, holder)) {
                listeners = holder.getNode();
                return true;
            }

            return false;
        }
    }

    private boolean addListenerIterative(final Object listenerToAdd, final ListenerNodeHolder holder) {
        ListenerNode currentNode = listeners;
        ListenerNode previousNode = null;

        while (currentNode != null) {
            if (comparator.compare(currentNode.listener, listenerToAdd) == 0) {
                return false;
            }
            previousNode = currentNode;
            currentNode = currentNode.next;
        }

        final ListenerNode newNode = new ListenerNode(listenerToAdd, comparator, null);
        if (previousNode == null) {
            holder.setNode(newNode);
        } else {
            previousNode.next = newNode;
            holder.setNode(listeners);
        }

        return true;
    }

    @Override
    public boolean clear() {
        synchronized (modifyLock) {
            final boolean listenersExist = (listeners != null);
            listeners = null;
            return listenersExist;
        }
    }

    @Override
    public boolean containsListener(final Object listenerToTest) {
        Check.notNull(listenerToTest, "listenerToTest");

        for (ListenerNode node = listeners; node != null; node = node.next) {
            if (comparator.compare(listenerToTest, node.listener) == 0) {
                return true;
            }
        }

        return false;
    }

    @Override
    public void foreachListener(final ListenerRunnable runnable) {
        foreachListener(DefaultExceptionHandler.INSTANCE, runnable);
    }

    @Override
    public void foreachListener(final ListenerExceptionHandler exceptionHandler, final ListenerRunnable runnable) {
        boolean keepGoing = true;

        for (ListenerNode node = listeners; node != null && keepGoing; node = node.next) {
            try {
                keepGoing = runnable.run(node.listener);
            } catch (final Exception e) {
                keepGoing = exceptionHandler.onException(node.listener, runnable, this, e);
            }
        }
    }

    @Override
    public Object[] getListeners() {
        return getListeners(new Object[] {});
    }

    @Override
    public Object[] getListeners(final Object[] a) {
        final List list = new ArrayList();

        for (ListenerNode node = listeners; node != null; node = node.next) {
            list.add(node.listener);
        }

        return list.toArray(a);
    }

    @Override
    public boolean removeListener(final Object listener) {
        Check.notNull(listener, "listener");

        synchronized (modifyLock) {
            if (listeners == null) {
                return false;
            }

            final ListenerNodeHolder holder = new ListenerNodeHolder();
            if (listeners.removeListener(listener, holder)) {
                listeners = holder.getNode();
                return true;
            }

            return false;
        }
    }

    @Override
    public int size() {
        int count = 0;

        for (ListenerNode node = listeners; node != null; node = node.next) {
            ++count;
        }

        return count;
    }

    private static class ListenerNode {
        public final Object listener;
        public final Comparator listenerComparator;
        public ListenerNode next;

        public ListenerNode(final Object listener, final Comparator listenerComparator, final ListenerNode next) {
            this.listener = listener;
            this.listenerComparator = listenerComparator;
            this.next = next;
        }

        public boolean removeListener(final Object listenerToRemove, final ListenerNodeHolder holder) {
            if (listenerComparator.compare(listener, listenerToRemove) == 0) {
                holder.setNode(next);
                return true;
            }

            if (next == null) {
                return false;
            } else {
                if (next.removeListener(listenerToRemove, holder)) {
                    holder.setNode(new ListenerNode(listener, listenerComparator, holder.getNode()));
                    return true;
                } else {
                    return false;
                }
            }
        }
    }

    private static class ListenerNodeHolder {
        private ListenerNode node;

        public ListenerNode getNode() {
            return node;
        }

        public void setNode(final ListenerNode node) {
            this.node = node;
        }
    }
}
