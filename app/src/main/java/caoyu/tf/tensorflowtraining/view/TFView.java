package caoyu.tf.tensorflowtraining.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

/**
 * 识别自定义框
 * Created by caoyu on 2018/5/29/029.
 */

public class TFView extends View {
    private static final String TAG = "TFView";

    private List<RectF> rectFArrayList;
    // 1.创建一个画笔
    private Paint mPaint = new Paint();

    // 2.初始化画笔
    private void initPaint() {
        mPaint.setColor(Color.RED);       //设置画笔颜色
        mPaint.setStyle(Paint.Style.STROKE);  //设置画笔模式为填充
        mPaint.setStrokeWidth(5f);         //设置画笔宽度为10px
        rectFArrayList = new ArrayList<>();
    }

    public TFView(Context context) {
        super(context);
        initPaint();
    }

    public TFView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        initPaint();
    }

    public TFView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initPaint();
    }

    @Override
    public void draw(Canvas canvas) {
        super.draw(canvas);
        for (int i = 0; i < rectFArrayList.size(); i++) {
//            RectF rectF = new RectF(100,100,800,400);
            canvas.drawRect(rectFArrayList.get(i), mPaint);
//            canvas.drawRect(rectF, mPaint);

        }

    }

    // 设置数据
    public void setData(List<RectF> mData) {
        this.rectFArrayList = mData;
        invalidate();   // 刷新
    }
}
