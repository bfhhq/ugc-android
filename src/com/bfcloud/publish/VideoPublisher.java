package com.bfcloud.publish;

import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class VideoPublisher {

    public interface ProgressListener {
        public void onProgress(int taskHandle, long pos, long max);
    }

    public interface CompleteListener {
        public void onComplete(int taskHandle, int errorCode, String errorMsg);
    }

    private static class TaskType {
        public final static int UPLOAD_TASK = 0;
        public final static int DELETE_TASK = 1;
    }

    private class TaskItem {
        public int taskType;
        public int taskHandle;
        public List<ProgressListener> progressListeners = new ArrayList<ProgressListener>();
        public List<CompleteListener> completeListeners = new ArrayList<CompleteListener>();

        public TaskItem(int taskType, int TaskHandle) {
            this.taskType = taskType;
            this.taskHandle = taskHandle;
        }
    }

    private volatile static VideoPublisher instance;
    private Map<Integer, TaskItem> mTasks = new HashMap<Integer, TaskItem>();

    private final static int MSG_PROGRESS = 1;
    private final static int MSG_COMPLETE = 2;

    private Handler msgHandler = new Handler() {
        public void handleMessage(Message msg){
            switch (msg.what) {
                case MSG_PROGRESS: {
                    Bundle data = msg.getData();
                    int taskHandle = data.getInt("taskHandle");
                    long pos = data.getLong("pos");
                    long max = data.getLong("max");

                    TaskItem taskItem = VideoPublisher.getInstance().mTasks.get(taskHandle);
                    if (taskItem != null) {
                        for (int i = 0; i < taskItem.progressListeners.size(); i++) {
                            taskItem.progressListeners.get(i).onProgress(taskHandle, pos, max);
                        }
                    }
                    break;
                }

                case MSG_COMPLETE: {
                    Bundle data = msg.getData();
                    int taskHandle = data.getInt("taskHandle");
                    int errorCode = data.getInt("errorCode");
                    String errorMsg = data.getString("errorMsg");

                    TaskItem taskItem = VideoPublisher.getInstance().mTasks.get(taskHandle);
                    if (taskItem != null) {
                        for (int i = 0; i < taskItem.completeListeners.size(); i++) {
                            taskItem.completeListeners.get(i).onComplete(taskHandle, errorCode, errorMsg);
                        }
                    }
                    break;
                }
            }
            super.handleMessage(msg);
        }
    };

    private VideoPublisher() {}

    public static VideoPublisher getInstance() {
        if (instance == null) {
            synchronized (VideoPublisher.class) {
                if (instance == null) {
                    instance = new VideoPublisher();
                }
            }
        }
        return instance;
    }

    public void init() {
        libpubInitialize();
        libpubSetSettingPath(getSdCardPath());
    }

    public void uninit() {
        libpubFinalize();
    }

    public int createUploadTask(String filePathName, String uploadToken) {
        int taskHandle = libpubUploadTaskCreate(filePathName, uploadToken);
        if (taskHandle != 0) {
            TaskItem taskItem = new TaskItem(TaskType.UPLOAD_TASK, taskHandle);
            mTasks.put(taskHandle, taskItem);
        }
        return taskHandle;
    }

    public int createDeleteTask(String deleteToken) {
        int taskHandle = libpubDeleteTaskCreate(deleteToken);
        if (taskHandle != 0) {
            TaskItem taskItem = new TaskItem(TaskType.DELETE_TASK, taskHandle);
            mTasks.put(taskHandle, taskItem);
        }
        return taskHandle;
    }

    public void destroyTask(int taskHandle) {
        libpubTaskDestroy(taskHandle);
        mTasks.remove(taskHandle);
    }

    public void registerProgressListener(int taskHandle, ProgressListener listener) {
        TaskItem taskItem = mTasks.get(taskHandle);
        if (taskItem != null) {
            taskItem.progressListeners.add(listener);
        }
    }

    public void unregisterProgressListener(int taskHandle, ProgressListener listener) {
        TaskItem taskItem = mTasks.get(taskHandle);
        if (taskItem != null) {
            taskItem.progressListeners.remove(listener);
        }
    }

    public void registerCompleteListener(int taskHandle, CompleteListener listener) {
        TaskItem taskItem = mTasks.get(taskHandle);
        if (taskItem != null) {
            taskItem.completeListeners.add(listener);
        }
    }

    public void unregisterCompleteListener(int taskHandle, CompleteListener listener) {
        TaskItem taskItem = mTasks.get(taskHandle);
        if (taskItem != null) {
            taskItem.completeListeners.remove(listener);
        }
    }

    public boolean startTask(int taskHandle) {
        return libpubTaskStart(taskHandle) == 0;
    }

    public boolean stopTask(int taskHandle) {
        return libpubTaskStop(taskHandle) == 0;
    }

    public static void onProgress(int taskHandle, long pos, long max) {
        Bundle data = new Bundle();
        data.putInt("taskHandle", taskHandle);
        data.putLong("pos", pos);
        data.putLong("max", max);

        Message msg = new Message();
        msg.what = MSG_PROGRESS;
        msg.setData(data);
        VideoPublisher.getInstance().msgHandler.sendMessage(msg);
    }

    public static void onComplete(int taskHandle, int errorCode, String errorMsg) {
        Bundle data = new Bundle();
        data.putInt("taskHandle", taskHandle);
        data.putInt("errorCode", errorCode);
        data.putString("errorMsg", errorMsg);

        Message msg = new Message();
        msg.what = MSG_COMPLETE;
        msg.setData(data);
        VideoPublisher.getInstance().msgHandler.sendMessage(msg);
    }

    private String getSdCardPath() {
        return Environment.getExternalStorageDirectory().getAbsolutePath() + File.separator;
    }

    static {
        try {
            System.loadLibrary("publish");
        } catch(UnsatisfiedLinkError e) {
            e.printStackTrace();
        }
    }

    private native void libpubInitialize();
    private native void libpubFinalize();
    private native int libpubUploadTaskCreate(String filePathName, String uploadToken);
    private native int libpubDeleteTaskCreate(String deleteToken);
    private native void libpubTaskDestroy(int taskHandle);
    private native int libpubTaskStart(int taskHandle);
    private native int libpubTaskStop(int taskHandle);
    private native void libpubSetSettingPath(String path);
}
