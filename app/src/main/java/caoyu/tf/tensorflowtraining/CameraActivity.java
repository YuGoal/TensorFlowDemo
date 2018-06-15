package caoyu.tf.tensorflowtraining;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.YuvImage;
import android.hardware.Camera;
import android.os.Looper;
import android.os.MessageQueue;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.FrameLayout;
import android.widget.TextView;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;

import caoyu.tf.tensorflowtraining.ternsorflow.Classifier;
import caoyu.tf.tensorflowtraining.ternsorflow.TensorFlowImageClassifier;
import caoyu.tf.tensorflowtraining.util.CameraUtils;
import caoyu.tf.tensorflowtraining.view.CameraSurfaceView;
import caoyu.tf.tensorflowtraining.view.TFView;

public class CameraActivity extends AppCompatActivity implements View.OnClickListener {

    private CameraSurfaceView mCameraSurfaceView;
    private static final int REQUEST_CAMERA = 0x01;
    private int mOrientation;

    // CameraSurfaceView 容器包装类
    private FrameLayout mAspectLayout;
    private boolean mCameraRequested;
    private CheckBox btnSwitch;
    private TFView tf_view;
    private TextView tv_classifier_info;
    private Paint paint;//画笔

    private static final int INPUT_SIZE = 224;
    private static final int IMAGE_MEAN = 117;
    private static final float IMAGE_STD = 1;
    private static final String INPUT_NAME = "input";
    private static final String OUTPUT_NAME = "output";
    private static final String MODEL_FILE = "file:///android_asset/model/tensorflow_inception_graph.pb";
    private static final String LABEL_FILE = "file:///android_asset/model/imagenet_comp_graph_label_strings.txt";

    private Executor executor;
    private Future future;
    private Classifier classifier;
    private  Runnable runnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);
        // Android 6.0相机动态权限检查
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            initView();
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{
                            Manifest.permission.CAMERA,
                            Manifest.permission.WRITE_EXTERNAL_STORAGE
                    }, REQUEST_CAMERA);
        }

        // 避免耗时任务占用 CPU 时间片造成UI绘制卡顿，提升启动页面加载速度
        Looper.myQueue().addIdleHandler(idleHandler);
    }

    /**
     * 初始化View
     */
    private void initView() {
        mAspectLayout = (FrameLayout) findViewById(R.id.layout_aspect);
        btnSwitch = (CheckBox) findViewById(R.id.btn_switch);
        tf_view = findViewById(R.id.tf_view);
        tv_classifier_info = findViewById(R.id.tv_classifier_info);
        mCameraSurfaceView = new CameraSurfaceView(this);
        mAspectLayout.addView(mCameraSurfaceView);
        mOrientation = CameraUtils.calculateCameraPreviewOrientation(this);
        btnSwitch.setOnClickListener(this);
    }

    /**
     * 主线程消息队列空闲时（视图第一帧绘制完成时）处理耗时事件
     */
    MessageQueue.IdleHandler idleHandler = new MessageQueue.IdleHandler() {
        @Override
        public boolean queueIdle() {

            if (classifier == null) {
                // 创建 Classifier
                classifier = TensorFlowImageClassifier.create(CameraActivity.this.getAssets(),
                        MODEL_FILE, LABEL_FILE, INPUT_SIZE, IMAGE_MEAN, IMAGE_STD, INPUT_NAME, OUTPUT_NAME);
            }

            // 初始化线程池
            executor = new ScheduledThreadPoolExecutor(1, new ThreadFactory() {
                @Override
                public Thread newThread(@NonNull Runnable r) {
                    Thread thread = new Thread(r);
                    thread.setDaemon(true);
                    thread.setName("ThreadPool-ImageClassifier");
                    return thread;
                }
            });
            return false;
        }
    };

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            // 相机权限
            case REQUEST_CAMERA:
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    mCameraRequested = true;
                    initView();
                }
                break;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mCameraRequested) {
            CameraUtils.startPreview();

        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        CameraUtils.stopPreview();
    }

    /**
     * 切换相机
     */
    private void switchCamera() {
        CameraUtils.takePicture(previewCallback);
    }

    /**
     * 视频预览回调
     */
    Camera.PreviewCallback previewCallback = new Camera.PreviewCallback() {
        @Override
        public void onPreviewFrame(byte[] data, Camera camera) {
            if (btnSwitch.isChecked()) {
                Camera.Size size = camera.getParameters().getPreviewSize();
                try {
                    YuvImage image = new YuvImage(data, ImageFormat.NV21, size.width, size.height, null);
                    if (image != null) {
                        ByteArrayOutputStream stream = new ByteArrayOutputStream();
                        image.compressToJpeg(new Rect(0, 0, size.width, size.height), 50, stream);
                        Bitmap bmp = getScaleBitmap(BitmapFactory.decodeByteArray(stream.toByteArray(), 0, stream.size()), INPUT_SIZE);
                        startImageClassifier(bmp);

                        stream.flush();
                        stream.close();
                    }
                } catch (Exception ex) {
                    Log.e("Sys", "Error:" + ex.getMessage());
                }
            } else {
                executor.execute(new Runnable() {
                    @Override
                    public void run() {

                    }
                });
                Looper.myQueue().removeIdleHandler(idleHandler);
                tv_classifier_info.setText("");
            }

        }
    };

    @Override
    public void onClick(View v) {
        switchCamera();
    }

    /**
     * 开始图片识别匹配
     *
     * @param bmp
     */
    private void startImageClassifier(final Bitmap bmp) {
        runnable = new Runnable() {
            @Override
            public void run() {
                Bitmap croppedBitmap = null;
                croppedBitmap = bmp;
                final List<Classifier.Recognition> results = classifier.recognizeImage(croppedBitmap);
                croppedBitmap.recycle();
                bmp.recycle();
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        drawRect(results);
                    }
                });
            }
        };
        executor.execute(runnable);
    }

    /**
     * 对图片进行缩放
     *
     * @param bitmap
     * @param size
     * @return
     * @throws IOException
     */
    private static Bitmap getScaleBitmap(Bitmap bitmap, int size) throws IOException {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        float scaleWidth = ((float) size) / width;
        float scaleHeight = ((float) size) / height;
        Matrix matrix = new Matrix();
        matrix.postScale(scaleWidth, scaleHeight);
        return Bitmap.createBitmap(bitmap, 0, 0, width, height, matrix, true);
    }

    /**
     * 绘制矩形
     *
     * @param recognitionList 识别结果
     */
    private void drawRect(List<Classifier.Recognition> recognitionList) {
        List<RectF> data = new ArrayList<>();
        for (int i = 0; i < recognitionList.size(); i++) {
            data.add(recognitionList.get(i).getLocation());

//            paint.setColor(Color.parseColor("#000000"));
//            canvas.drawRect(recognitionList.get(0).getLocation(), paint);
            Log.e("TensorFlow", "drawRect: " + recognitionList.get(i) + "\n" +
                    recognitionList.get(i).getLocation().toString() + "\n");
            tv_classifier_info.setText(recognitionList.get(i).toString());
        }
        tf_view.setData(data);
    }

}
