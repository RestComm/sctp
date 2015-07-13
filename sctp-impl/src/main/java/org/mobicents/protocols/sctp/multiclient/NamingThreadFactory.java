package org.mobicents.protocols.sctp.multiclient;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Thread factory which names threads by "pool-<basename>-thread-n".
 * This is a replacement for Executors.defaultThreadFactory() to be able to identify pools.
 * Optionally a delegate thread factory can be given which creates the Thread
 * object itself, if no delegate has been given, Executors.defaultThreadFactory is used.
 * @author pocsaji.miklos@alerant.hu
 *
 */
public class NamingThreadFactory implements ThreadFactory {

    private final ThreadGroup group;
    private String baseName;
    private final AtomicInteger index = new AtomicInteger(1);

    public NamingThreadFactory(String baseName) {
        this.baseName = baseName;
        SecurityManager s = System.getSecurityManager();
        group = (s != null) ? s.getThreadGroup() :
                              Thread.currentThread().getThreadGroup();
    }

     public Thread newThread(Runnable r) {
        Thread t = new Thread(group, r,
        					"pool-" + baseName + "-thread-" + index.getAndIncrement(),
                              0);
        if (t.isDaemon())
            t.setDaemon(false);
        if (t.getPriority() != Thread.NORM_PRIORITY)
            t.setPriority(Thread.NORM_PRIORITY);
        return t;
    }
}
