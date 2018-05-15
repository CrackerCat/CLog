package com.cjz.clog;

import android.os.Environment;
import android.text.TextUtils;
import android.util.Log;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

public class CLog {

    private static final String TAG = CLog.class.getName();

    private static String logPath;

    public static void logFilePath(String path){
        if(TextUtils.isEmpty(path)){
            logPath = Environment.getExternalStorageDirectory().getAbsolutePath();
        }
        logPath = path;

    }

    static String getLogPath() {
        logFilePath(logPath);
        return logPath;
    }


    private static String getLocation() {
        final String className = CLog.class.getName();
        final StackTraceElement[] traces = Thread.currentThread().getStackTrace();
        boolean found = false;

        for (StackTraceElement trace : traces) {
            try {
                if (found) {
                    if (!trace.getClassName().startsWith(className)) {
                        Class<?> clazz = Class.forName(trace.getClassName());

                        //eclipse下点击日志跳转到代码行
                        // return "at (" + getClassName(clazz) + ".java:" + trace.getLineNumber() + ") " + trace.getMethodName() + "():";

                        //android studio下点击日志跳转到代码行
                        return "at " + trace.getClassName() + "." + trace.getMethodName() + "(" + getClassName(clazz) + ".java:" + trace.getLineNumber() + ") :";
                    }
                } else if (trace.getClassName().startsWith(className)) {
                    found = true;
                }
            } catch (ClassNotFoundException ignored) {
            }
        }

        return "";
    }

    private static String getClassName(Class<?> clazz) {
        if (clazz != null) {
            if (!TextUtils.isEmpty(clazz.getSimpleName())) {
                return clazz.getSimpleName();
            }
            return getClassName(clazz.getEnclosingClass());
        }
        return "";
    }

    public static void v(String msg) {
        Log.v(TAG, getLocation() + msg);
    }

    public static void vt(String msg) {
        String msgWithThreadInfo = Thread.currentThread().getId() + Thread.currentThread().getName() + msg;
        v(msgWithThreadInfo);
    }

    public static void v(String tag, String msg) {
        Log.v(tag, getLocation() + msg);
    }

    public static void i(String msg) {
        Log.i(TAG, getLocation() + msg);
    }

    public static void i(String tag, String msg) {
        Log.i(tag, getLocation() + msg);
    }

    public static void w(String msg) {
        Log.w(TAG, getLocation() + msg);
    }

    public static void w(String tag, String msg) {
        Log.w(tag, getLocation() + msg);
    }

    public static void d(String msg) {
        Log.d(TAG, getLocation() + msg);
    }

    public static void d(String tag, String msg) {
        Log.d(tag, getLocation() + msg);
    }

    public static void e(String msg) {
        Log.e(TAG, getLocation() + msg);
    }

    public static void e(String tag, String msg) {
        Log.e(tag, getLocation() + msg);
    }

    public static void e(String msg, Throwable t) {
        Log.e(TAG, getLocation() + msg, t);
    }

    public static void e(String tag, String msg, Throwable t) {
        Log.e(tag, getLocation() + msg, t);
    }


    /**
     * 日志写文件,级别同i,文件名自动按照小时数区分
     */
    public static void f(String msg, Throwable t) {
        f(TAG, getLocation() + msg, t);
    }

    /**
     * 日志写文件,级别同i,文件名自动按照小时数区分
     */
    public static void f(String tag, String msg) {
        Log.i(tag, getLocation() + msg);
        logToFile(tag, getLocation() + msg, null, false);
    }

    /**
     * 日志写文件,级别同i,文件名自动按照小时数区分
     */
    public static void f(String tag, String msg, Throwable t) {
        Log.i(tag, getLocation() + msg, t);
        logToFile(tag, getLocation() + msg, t, false);
    }

    public static void fTagAsLogFileName(String tag, String msg) {
        String s = getLocation() + msg;
        Log.i(tag, s, null);
        logToFile(tag, s, null, false);
        logToFile(tag, s, null, true);
    }

    private static boolean mInitialized;
    private static ReentrantLock mFileLock;
    private static ConcurrentLinkedQueue<Entry> mEntryQueue;
    private static AtomicBoolean mWriteThreadRunning;
    private static WriteThread mWriteThread;

    /**
     * 可选调用，销毁写日志占用的资源
     */
    public static void destroyF() {
        mFileLock = null;
        mEntryQueue = null;
        mWriteThreadRunning = null;
        mInitialized = false;
        if (mWriteThread != null) {
            mWriteThread.interrupt();
        }
        mWriteThread = null;
    }

    /**
     * 可选调用，程序退出时，可等待写线程完成
     */
    public static void waitUntilFinishedWriting() {
        if (mInitialized) {
            if (mWriteThread != null && mWriteThreadRunning.get()) {
                try {
                    mWriteThread.join();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * 日志写入文件
     *
     * @param tag           日志tag
     * @param msg           日志内容
     * @param t
     * @param tagAsFileName 是否将日志文件按照tag命名
     */
    private static void logToFile(String tag, String msg, Throwable t, boolean tagAsFileName) {

        if (!mInitialized) {
            mFileLock = new ReentrantLock();
            mEntryQueue = new ConcurrentLinkedQueue<Entry>();
            mWriteThreadRunning = new AtomicBoolean(false);
            mInitialized = true;
        }

        if (mInitialized) {
            addEntryToStack(tag, msg, t, tagAsFileName);
        }
    }

    private static void addEntryToStack(String tag, String msg, Throwable tr, boolean tagAsFileName) {
        long now = System.currentTimeMillis();

        Entry currentEntry = new Entry(now, tag, msg, tr, tagAsFileName);

        mEntryQueue.add(currentEntry);

        startWriteThread();
    }

    private static void startWriteThread() {
        if (mWriteThread == null || !mWriteThread.isAlive()) {
            mFileLock.lock();
            try {
                if (mWriteThread == null || !mWriteThread.isAlive()) {
                    mWriteThreadRunning.set(true);
                    mWriteThread = new WriteThread();
                    mWriteThread.start();
                }
            } finally {
                mFileLock.unlock();
            }
        }
    }

    private static final class WriteThread extends Thread {
        private static final long THREAD_KEEP_SELF_ALIVE = 10000;
        private static final long NO_WORK_SLEEP = 200;

        private long mLastWriteTime = 0;

        @Override
        public synchronized void start() {
            super.start();

            mLastWriteTime = System.currentTimeMillis();
        }

        @Override
        public void run() {
            while (!isInterrupted() && mWriteThreadRunning.get()) {

                //有任务存在
                if (!mEntryQueue.isEmpty()) {

//方案一:读取队列里的所有任务一次输出到文件,但无法区分每个日志的tag
//                    StringBuilder stringBuilder = new StringBuilder();
//                    Entry currentEntry;
//                    while ((currentEntry = mEntryQueue.poll()) != null) {
//                        currentEntry.convertToString(stringBuilder);
//                    }
//                    mFileLock.lock();
//                    try {
//                        writeToFile(stringBuilder.toString());
//                    } finally {
//                        mFileLock.unlock();
//                    }

                    //方案二:区别对待每条日志,根据日志的设置是否写入到单独的文件中
                    Entry currentEntry;
                    while ((currentEntry = mEntryQueue.poll()) != null) {
                        mFileLock.lock();
                        try {
                            writeToFile(currentEntry);
                        } finally {
                            mFileLock.unlock();
                        }
                    }
                    mLastWriteTime = System.currentTimeMillis();
                }
                //间隔10秒都没有没有新任务，线程自行销毁
                else if ((System.currentTimeMillis() - mLastWriteTime) > THREAD_KEEP_SELF_ALIVE) {
                    mWriteThreadRunning.set(false);
                }
                //没有任务时使线程循环进入休眠状态，不至于抢占CPU
                else {
                    try {
                        Thread.sleep(NO_WORK_SLEEP);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    private static void writeToFile(Entry currentEntry) {
        try {
            if (mInitialized) {
                BufferedWriter bufferedWriter = new BufferedWriter(getFileWriter(currentEntry));
                bufferedWriter.write(currentEntry.toString());
                bufferedWriter.close();
            }
        } catch (IOException ioException) {
            ioException.printStackTrace();
        }
    }

    private static final String mNewLine = System.getProperty("line.separator");
    private static final SimpleDateFormat mSimpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US);
    private static SimpleDateFormat logFileFormatter = new SimpleDateFormat("yyyy_MM_dd_HH", Locale.US);

    private static FileWriter getFileWriter(Entry entry) throws IOException {
        String logfileName = getLogPath() + File.separator + (entry.isTagAsFileName() ? entry.getTag() : "") + entry.getLogHappenedHour() + ".txt";

        File logFile = new File(logfileName);
        if (!logFile.exists()) {
            if (!logFile.getParentFile().exists()) {
                logFile.getParentFile().mkdirs();
            }
            logFile.createNewFile();
        }

        return new FileWriter(logFile, true);
    }

    private static final class Entry {
        private long timestamp;
        private String tag;
        private String msg;
        private Throwable tr;
        private boolean tagAsFileName;

        public String getLogHappenedHour() {
            Calendar calendar = Calendar.getInstance();
            calendar.setTimeInMillis(timestamp);
            calendar.set(Calendar.MINUTE, 0);
            calendar.set(Calendar.SECOND, 0);
            calendar.set(Calendar.MILLISECOND, 0);
            return logFileFormatter.format(calendar.getTime());
        }

        public String getTag() {
            return tag;
        }

        public boolean isTagAsFileName() {
            return tagAsFileName;
        }

        public Entry(long timestamp, String tag, String msg, Throwable tr, boolean tagAsFileName) {
            this.timestamp = timestamp;
            this.tag = tag;
            this.msg = msg;
            this.tr = tr;
            this.tagAsFileName = tagAsFileName;
        }

        @Override
        public String toString() {
            StringBuilder stringBuilder = new StringBuilder();
            Date date = new Date();
            date.setTime(timestamp);
            stringBuilder.append("[");
            stringBuilder.append(mSimpleDateFormat.format(date));
            stringBuilder.append("]:");
            stringBuilder.append(mNewLine);
            if (tag != null) {
                stringBuilder.append(" TAG:").append(tag);
            }
            if (msg != null) {
                stringBuilder.append(" MSG:").append(msg);
            }
            if (tr != null) {
                stringBuilder.append(" Trace:").append(getStackTraceString(tr));
            }
            stringBuilder.append(mNewLine);
            return stringBuilder.toString();
        }
    }

    public static String getStackTraceString(Throwable tr) {
        return android.util.Log.getStackTraceString(tr);
    }
}

