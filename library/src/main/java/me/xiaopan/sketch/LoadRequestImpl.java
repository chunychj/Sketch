/*
 * Copyright (C) 2013 Peng fei Pan <sky@xiaopan.me>
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package me.xiaopan.sketch;

import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.os.Message;
import android.util.Log;
import android.widget.ImageView;

import java.io.File;

import me.xiaopan.sketch.download.ImageDownloader;
import me.xiaopan.sketch.process.ImageProcessor;
import me.xiaopan.sketch.util.CommentUtils;

/**
 * 加载请求
 */
public class LoadRequestImpl implements LoadRequest, Runnable{
    private static final int WHAT_CALLBACK_COMPLETED = 202;
    private static final int WHAT_CALLBACK_FAILED = 203;
    private static final int WHAT_CALLBACK_CANCELED = 204;
    private static final int WHAT_CALLBACK_PROGRESS = 205;
    private static final String NAME = "LoadRequestImpl";

    // Base fields
    private Sketch sketch;  // Sketch
    private String uri;	// 图片地址
    private String name;	// 名称，用于在输出LOG的时候区分不同的请求
    private UriScheme uriScheme;	// Uri协议格式
    private RequestLevel requestLevel = RequestLevel.NET;  // 请求Level
    private RequestLevelFrom requestLevelFrom; // 请求Level的来源

    // Download fields
    private boolean enableDiskCache = true;	// 是否开启磁盘缓存
    private ProgressListener progressListener;  // 下载进度监听器

    // Load fields
    private ImageSize resize;	// 裁剪尺寸，ImageProcessor会根据此尺寸和scaleType来裁剪图片
    private ImageSize maxSize;	// 最大尺寸，用于读取图片时计算inSampleSize
    private ImageProcessor imageProcessor;	// 图片处理器
    private ImageView.ScaleType scaleType; // 图片缩放方式，ImageProcessor会根据resize和scaleType来创建新的图片
    private LoadListener loadListener;	// 监听器
    private boolean decodeGifImage = true;  // 是否解码GIF图片

    // Runtime fields
    private File cacheFile;	// 缓存文件
    private byte[] imageData;
    private Drawable resultBitmap;
    private String mimeType;
    private ImageFrom imageFrom;    // 图片来源
    private FailCause failCause;    // 失败原因
    private RunStatus runStatus = RunStatus.DISPATCH;    // 运行状态，用于在执行run方法时知道该干什么
    private CancelCause cancelCause;  // 取消原因
    private RequestStatus requestStatus = RequestStatus.WAIT_DISPATCH;  // 状态

    public LoadRequestImpl(Sketch sketch, String uri, UriScheme uriScheme) {
        this.sketch = sketch;
        this.uri = uri;
        this.uriScheme = uriScheme;
    }

    /****************************************** Base methods ******************************************/
    @Override
    public Sketch getSketch() {
        return sketch;
    }

    @Override
    public String getUri() {
        return uri;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public void setName(String name) {
        this.name = name;
    }

    @Override
    public UriScheme getUriScheme() {
        return uriScheme;
    }

    @Override
    public void setRequestLevel(RequestLevel requestLevel) {
        this.requestLevel = requestLevel;
    }

    @Override
    public void setRequestLevelFrom(RequestLevelFrom requestLevelFrom) {
        this.requestLevelFrom = requestLevelFrom;
    }

    /****************************************** Download methods ******************************************/
    @Override
    public void setEnableDiskCache(boolean enableDiskCache) {
        this.enableDiskCache = enableDiskCache;
    }

    @Override
    public boolean isEnableDiskCache() {
        return enableDiskCache;
    }

    @Override
    public void setProgressListener(ProgressListener progressListener) {
        this.progressListener = progressListener;
    }

    /****************************************** Load methods ******************************************/
    @Override
    public ImageSize getResize() {
        return resize;
    }

    @Override
    public void setResize(ImageSize resize) {
        this.resize = resize;
    }

    @Override
    public ImageSize getMaxSize() {
        return maxSize;
    }

    @Override
    public void setMaxSize(ImageSize maxSize) {
        this.maxSize = maxSize;
    }

    @Override
    public ImageView.ScaleType getScaleType() {
        return scaleType;
    }

    @Override
    public void setScaleType(ImageView.ScaleType scaleType) {
        this.scaleType = scaleType;
    }

    @Override
    public ImageProcessor getImageProcessor() {
        return imageProcessor;
    }

    @Override
    public void setImageProcessor(ImageProcessor imageProcessor) {
        this.imageProcessor = imageProcessor;
    }

    @Override
    public void setLoadListener(LoadListener loadListener) {
        this.loadListener = loadListener;
    }

    @Override
    public boolean isDecodeGifImage() {
        return decodeGifImage;
    }

    @Override
    public void setDecodeGifImage(boolean isDecodeGifImage) {
        this.decodeGifImage = isDecodeGifImage;
    }

    /****************************************** Runtime methods ******************************************/
    @Override
    public File getCacheFile() {
        return cacheFile;
    }

    @Override
    public byte[] getImageData() {
        return imageData;
    }

    @Override
    public ImageFrom getImageFrom() {
        return imageFrom;
    }

    @Override
    public RequestStatus getRequestStatus() {
        return requestStatus;
    }

    @Override
    public void setRequestStatus(RequestStatus requestStatus) {
        this.requestStatus = requestStatus;
    }

    @Override
    public void setDownloadListener(DownloadListener downloadListener) {
    }

    @Override
    public FailCause getFailCause() {
        return failCause;
    }

    @Override
    public CancelCause getCancelCause() {
        return cancelCause;
    }

    /****************************************** Other methods ******************************************/
    @Override
    public boolean isFinished() {
        return requestStatus == RequestStatus.COMPLETED || requestStatus == RequestStatus.CANCELED || requestStatus == RequestStatus.FAILED;
    }

    @Override
    public boolean isCanceled() {
        return requestStatus == RequestStatus.CANCELED;
    }

    @Override
    public boolean cancel() {
        if(isFinished()){
            return false;
        }
        toCanceledStatus(CancelCause.NORMAL);
        return true;
    }

    @Override
    public void postRunDispatch() {
        setRequestStatus(RequestStatus.WAIT_DISPATCH);
        this.runStatus = RunStatus.DISPATCH;
        sketch.getConfiguration().getRequestExecutor().getRequestDispatchExecutor().execute(this);
    }

    @Override
    public void postRunDownload() {
        setRequestStatus(RequestStatus.WAIT_DOWNLOAD);
        this.runStatus = RunStatus.DOWNLOAD;
        sketch.getConfiguration().getRequestExecutor().getNetRequestExecutor().execute(this);
    }

    @Override
    public void postRunLoad() {
        setRequestStatus(RequestStatus.WAIT_LOAD);
        this.runStatus = RunStatus.LOAD;
        sketch.getConfiguration().getRequestExecutor().getLocalRequestExecutor().execute(this);
    }

    @Override
    public void updateProgress(int totalLength, int completedLength) {
        if(progressListener != null){
            sketch.getConfiguration().getHandler().obtainMessage(WHAT_CALLBACK_PROGRESS, totalLength, completedLength, this).sendToTarget();
        }
    }

    @Override
    public void toFailedStatus(FailCause failCause) {
        this.failCause = failCause;
        sketch.getConfiguration().getHandler().obtainMessage(WHAT_CALLBACK_FAILED, this).sendToTarget();
    }

    @Override
    public void toCanceledStatus(CancelCause cancelCause) {
        this.cancelCause = cancelCause;
        setRequestStatus(RequestStatus.CANCELED);
        sketch.getConfiguration().getHandler().obtainMessage(WHAT_CALLBACK_CANCELED, this).sendToTarget();
    }

    @Override
    public void invokeInMainThread(Message msg) {
        switch (msg.what){
            case WHAT_CALLBACK_COMPLETED:
                handleCompletedOnMainThread();
                break;
            case WHAT_CALLBACK_PROGRESS :
                updateProgressOnMainThread(msg.arg1, msg.arg2);
                break;
            case WHAT_CALLBACK_FAILED:
                handleFailedOnMainThread();
                break;
            case WHAT_CALLBACK_CANCELED:
                handleCanceledOnMainThread();
                break;
            default:
                new IllegalArgumentException("unknown message what: "+msg.what).printStackTrace();
                break;
        }
    }

    @Override
    public void run() {
        switch(runStatus){
            case DISPATCH:
                executeDispatch();
                break;
            case LOAD:
                executeLoad();
                break;
            case DOWNLOAD:
                executeDownload();
                break;
            default:
                new IllegalArgumentException("unknown runStatus: "+runStatus.name()).printStackTrace();
                break;
        }
    }

    /**
     * 执行分发
     */
    private void executeDispatch() {
        setRequestStatus(RequestStatus.DISPATCHING);
        if(uriScheme == UriScheme.HTTP || uriScheme == UriScheme.HTTPS){
            File diskCacheFile = enableDiskCache? sketch.getConfiguration().getDiskCache().getCacheFile(uri):null;
            if(diskCacheFile != null && diskCacheFile.exists()){
                this.cacheFile = diskCacheFile;
                this.imageFrom = ImageFrom.DISK_CACHE;
                postRunLoad();
                if(Sketch.isDebugMode()){
                    Log.d(Sketch.TAG, CommentUtils.concat(NAME, " - ", "executeDispatch", " - ", "diskCache", " - ", name));
                }
            }else{
                if(requestLevel == RequestLevel.LOCAL){
                    if(requestLevelFrom == RequestLevelFrom.PAUSE_DOWNLOAD){
                        toCanceledStatus(CancelCause.PAUSE_DOWNLOAD);
                        if(Sketch.isDebugMode()){
                            Log.w(Sketch.TAG, CommentUtils.concat(NAME, " - ", "canceled", " - ", "pause download", " - ", name));
                        }
                    }else{
                        toCanceledStatus(CancelCause.LEVEL_IS_LOCAL);
                        if(Sketch.isDebugMode()){
                            Log.w(Sketch.TAG, CommentUtils.concat(NAME, " - ", "canceled", " - ", "requestLevel is local", " - ", name));
                        }
                    }
                    return;
                }

                postRunDownload();
                if(Sketch.isDebugMode()){
                    Log.d(Sketch.TAG, CommentUtils.concat(NAME, " - ", "executeDispatch", " - ", "download", " - ", name));
                }
            }
        }else{
            this.imageFrom = ImageFrom.LOCAL;
            postRunLoad();
            if(Sketch.isDebugMode()){
                Log.d(Sketch.TAG, CommentUtils.concat(NAME, " - ", "executeDispatch", " - ", "local", " - ", name));
            }
        }
    }

    /**
     * 执行下载
     */
    private void executeDownload() {
        if(isCanceled()){
            if(Sketch.isDebugMode()){
                Log.w(Sketch.TAG, CommentUtils.concat(NAME, " - ", "executeDownload", " - ", "canceled", " - ", "startDownload", " - ", name));
            }
            return;
        }

        ImageDownloader.DownloadResult downloadResult = sketch.getConfiguration().getImageDownloader().download(this);

        if(isCanceled()){
            if(Sketch.isDebugMode()){
                Log.w(Sketch.TAG, CommentUtils.concat(NAME, " - ", "executeDownload", " - ", "canceled", " - ", "downloadAfter", " - ", name));
            }
            return;
        }

        if(downloadResult != null  && downloadResult.getResult() != null){
            if(downloadResult.getResult().getClass().isAssignableFrom(File.class)){
                this.cacheFile = (File) downloadResult.getResult();
            }else{
                this.imageData = (byte[]) downloadResult.getResult();
            }
            this.imageFrom = downloadResult.isFromNetwork()?ImageFrom.NETWORK:ImageFrom.DISK_CACHE;
            postRunLoad();
        }else{
            toFailedStatus(FailCause.DOWNLOAD_FAIL);
        }
    }

    /**
     * 执行加载
     */
    private void executeLoad(){
        if(isCanceled()){
            if(Sketch.isDebugMode()){
                Log.w(Sketch.TAG, CommentUtils.concat(NAME, " - ", "executeLoad", " - ", "canceled", " - ", "startLoad", " - ", name));
            }
            return;
        }

        setRequestStatus(RequestStatus.LOADING);

        // 如果是本地APK文件就尝试得到其缓存文件
        if(isLocalApkFile()){
            File apkIconCacheFile = getApkCacheIconFile();
            if(apkIconCacheFile != null){
                this.cacheFile = apkIconCacheFile;
            }
        }

        // 解码
        Object decodeResult = sketch.getConfiguration().getImageDecoder().decode(this);
        if(decodeResult == null){
            toFailedStatus(FailCause.DECODE_FAIL);
        }

        if(decodeResult instanceof Bitmap){
            Bitmap bitmap = (Bitmap) decodeResult;
            if(!bitmap.isRecycled()){
                if(Sketch.isDebugMode()){
                    Log.d(Sketch.TAG, CommentUtils.concat(NAME, " - ", "executeLoad", " - ", "new bitmap@", Integer.toHexString(bitmap.hashCode()), " - ", "executeLoad", " - ", name));
                }
            }else{
                if(Sketch.isDebugMode()){
                    Log.e(Sketch.TAG, CommentUtils.concat(NAME, " - ", "executeLoad", " - ", "decodeFailed", " - ", name));
                }
            }

            if(isCanceled()){
                if(Sketch.isDebugMode()){
                    Log.w(Sketch.TAG, CommentUtils.concat(NAME, " - ", "executeLoad", " - ", "recycle bitmap@", Integer.toHexString(bitmap.hashCode()), " - ", "decodeAfter:cancel", " - ", name));
                }
                bitmap.recycle();
                if(Sketch.isDebugMode()){
                    Log.w(Sketch.TAG, CommentUtils.concat(NAME, " - ", "executeLoad", " - ", "canceled", " - ", "decodeAfter", " - ", name));
                }
                return;
            }

            //处理
            if(!bitmap.isRecycled()){
                ImageProcessor imageProcessor = getImageProcessor();
                if(imageProcessor == null && getResize() != null){
                    imageProcessor = sketch.getConfiguration().getDefaultCutImageProcessor();
                }
                if(imageProcessor != null){
                    Bitmap newBitmap = imageProcessor.process(bitmap, getResize(), getScaleType());
                    if(newBitmap != null && newBitmap != bitmap && Sketch.isDebugMode()){
                        Log.w(Sketch.TAG, CommentUtils.concat(NAME, " - ", "executeLoad", " - ", "new bitmap@"+Integer.toHexString(newBitmap.hashCode())+" - ", "recycle old bitmap@", Integer.toHexString(bitmap.hashCode()), " - ", "processAfter", " - ", name));
                    }
                    if(newBitmap == null || newBitmap != bitmap){
                        bitmap.recycle();
                    }
                    bitmap = newBitmap;
                }
            }

            if(isCanceled()){
                if(bitmap != null){
                    if(Sketch.isDebugMode()){
                        Log.w(Sketch.TAG, CommentUtils.concat(NAME, " - ", "executeLoad", " - ", "recycle bitmap@", Integer.toHexString(bitmap.hashCode()), "processAfter:cancel"));
                    }
                    bitmap.recycle();
                }
                if(Sketch.isDebugMode()){
                    Log.w(Sketch.TAG, CommentUtils.concat(NAME, " - ", "executeLoad", " - ", "canceled ", " - ", "processAfter", " - ", name));
                }
                return;
            }

            if(bitmap != null && !bitmap.isRecycled()){
                RecycleBitmapDrawable recycleBitmapDrawable = new RecycleBitmapDrawable(bitmap);
                recycleBitmapDrawable.setMimeType(mimeType);
                this.resultBitmap = recycleBitmapDrawable;
                sketch.getConfiguration().getHandler().obtainMessage(WHAT_CALLBACK_COMPLETED, this).sendToTarget();
            }else{
                toFailedStatus(FailCause.DECODE_FAIL);
            }
        }else if(decodeResult instanceof RecycleGifDrawable){
            RecycleGifDrawable recycleGifDrawable = (RecycleGifDrawable) decodeResult;
            recycleGifDrawable.setMimeType(mimeType);
            this.resultBitmap = recycleGifDrawable;
            sketch.getConfiguration().getHandler().obtainMessage(WHAT_CALLBACK_COMPLETED, this).sendToTarget();
        }else{
            toFailedStatus(FailCause.DECODE_FAIL);
        }
    }

    @Override
    public boolean isLocalApkFile(){
        return uriScheme == UriScheme.FILE && CommentUtils.checkSuffix(uri, ".apk");
    }

    @Override
    public String getMimeType() {
        return mimeType;
    }

    @Override
    public void setMimeType(String mimeType) {
        this.mimeType = mimeType;
    }

    /**
     * 获取APK图片的缓存文件
     * @return APK图片的缓存文件
     */
    private File getApkCacheIconFile(){
        File apkIconCacheFile = sketch.getConfiguration().getDiskCache().getCacheFile(uri);
        if(apkIconCacheFile != null){
            return apkIconCacheFile;
        }

        Bitmap iconBitmap = CommentUtils.decodeIconFromApk(sketch.getConfiguration().getContext(), uri, NAME);
        if(iconBitmap != null && !iconBitmap.isRecycled()){
            apkIconCacheFile = sketch.getConfiguration().getDiskCache().saveBitmap(iconBitmap, uri);
            if(apkIconCacheFile != null){
                return apkIconCacheFile;
            }
        }

        return null;
    }

    private void handleCompletedOnMainThread() {
        if(isCanceled()){
            if(resultBitmap != null){
                ((RecycleDrawableInterface)resultBitmap).recycle();
            }
            if(Sketch.isDebugMode()){
                Log.w(Sketch.TAG, CommentUtils.concat(NAME, " - ", "handleCompletedOnMainThread", " - ", "canceled", " - ", name));
            }
            return;
        }

        setRequestStatus(RequestStatus.COMPLETED);
        if(loadListener != null){
            loadListener.onCompleted(resultBitmap, imageFrom, mimeType);
        }
    }

    private void handleFailedOnMainThread() {
        if(isCanceled()){
            if(Sketch.isDebugMode()){
                Log.w(Sketch.TAG, CommentUtils.concat(NAME, " - ", "handleFailedOnMainThread", " - ", "canceled", " - ", name));
            }
            return;
        }

        setRequestStatus(RequestStatus.FAILED);
        if(loadListener != null){
            loadListener.onFailed(failCause);
        }
    }

    private void handleCanceledOnMainThread() {
        if(loadListener != null){
            loadListener.onCanceled(cancelCause);
        }
    }

    private void updateProgressOnMainThread(int totalLength, int completedLength) {
        if(isFinished()){
            if(Sketch.isDebugMode()){
                Log.w(Sketch.TAG, CommentUtils.concat(NAME, " - ", "updateProgressOnMainThread", " - ", "finished", " - ", name));
            }
            return;
        }

        if(progressListener != null){
            progressListener.onUpdateProgress(totalLength, completedLength);
        }
    }
}