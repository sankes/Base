package com.shankes.base.activity;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.Fragment;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.FileProvider;
import android.util.Log;
import android.widget.Toast;

import com.shankes.base.R;

import java.io.File;
import java.io.IOException;

/**
 * 相机相册
 *
 * @author shankes
 */
public class PhotoCaptureActivity extends Activity {

    private static final String TAG = PhotoCaptureActivity.class.getSimpleName();
    private String state;
    private Uri imageUri;
    /**
     * 从相机选择
     */
    private static final int TAKE_PHOTO = 1;
    /**
     * 从相册选择
     */
    private static final int PICTURE_CAPTURE = 2;
    /**
     * 从相机选择后截图
     */
    private static final int ZOOM_AFTER_TAKE_PHOTO = 3;
    /**
     * 从照片选择后截图
     */
    private static final int ZOOM_AFTER_PICTURE_CAPTURE = 4;

    private int outputX = 800;
    private int outputY = 800;

    public enum CaptureType {
        // 从相机选择
        TAKE_PHOTO,
        // 从相册选择
        PICTURE_CAPTURE,
        // 从相机选择后截图
        ZOOM_AFTER_TAKE_PHOTO,
        // 从照片选择后截图
        ZOOM_AFTER_PICTURE_CAPTURE
    }

    private CaptureType mCaptureType;

    public static void capturePhoto(Activity activity, CaptureType captureType, int requestCode) {

        Intent intent = new Intent(activity, PhotoCaptureActivity.class);
        Bundle bundle = new Bundle();
        bundle.putSerializable("capture_type", captureType);
        intent.putExtras(bundle);
        activity.startActivityForResult(intent, requestCode);

    }

    public static void capturePhoto(Fragment fragment, CaptureType captureType, int requestCode) {
        Intent intent = new Intent(fragment.getContext(), PhotoCaptureActivity.class);
        Bundle bundle = new Bundle();
        bundle.putSerializable("capture_type", captureType);
        intent.putExtras(bundle);
        fragment.startActivityForResult(intent, requestCode);
    }

    public static void capturePhoto(Activity activity, CaptureType captureType, int outputX, int outputY, int requestCode) {
        if (captureType != CaptureType.ZOOM_AFTER_TAKE_PHOTO && captureType != CaptureType.ZOOM_AFTER_PICTURE_CAPTURE) {
            throw new RuntimeException("获取照片方式的类型参数异常");
        }
        Intent intent = new Intent(activity, PhotoCaptureActivity.class);
        intent.putExtra("outputX", outputX);
        intent.putExtra("outputY", outputY);
        Bundle bundle = new Bundle();
        bundle.putSerializable("capture_type", captureType);
        intent.putExtras(bundle);
        activity.startActivityForResult(intent, requestCode);
    }

    public static void capturePhoto(Fragment fragment, CaptureType captureType, int outputX, int outputY, int requestCode) {
        if (captureType != CaptureType.ZOOM_AFTER_TAKE_PHOTO && captureType != CaptureType.ZOOM_AFTER_PICTURE_CAPTURE) {
            throw new RuntimeException("获取照片方式的类型参数异常");
        }
        Intent intent = new Intent(fragment.getContext(), PhotoCaptureActivity.class);
        intent.putExtra("outputX", outputX);
        intent.putExtra("outputY", outputY);
        Bundle bundle = new Bundle();
        bundle.putSerializable("capture_type", captureType);
        intent.putExtras(bundle);
        fragment.startActivityForResult(intent, requestCode);
    }

    private SharedPreferences mPreferences;

    private static final int WRITE_SDCARD_PERMISSION_REQUEST_CODE = 622;
    private static final int TAKE_PHOTO_PERMISSION_REQUEST_CODE = 623;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mCaptureType = (CaptureType) getIntent().getExtras().get("capture_type");
        outputX = getIntent().getIntExtra("outputX", 800);
        outputY = getIntent().getIntExtra("outputY", 800);

        mPreferences = getPreferences(MODE_PRIVATE);

        // 先判断用户以前有没有对我们的应用程序允许过读写内存卡内容的权限，
        // 用户处理的结果在 onRequestPermissionResult 中进行处理
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            // 申请读写内存卡内容的权限
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, WRITE_SDCARD_PERMISSION_REQUEST_CODE);
        }
        // 存储介质
        state = Environment.getExternalStorageState();
        if (mCaptureType == CaptureType.TAKE_PHOTO) {
            takePhoto();
        } else if (mCaptureType == CaptureType.PICTURE_CAPTURE) {
            selectPhoto();
        } else if (mCaptureType == CaptureType.ZOOM_AFTER_TAKE_PHOTO) {
            takePhoto();
        } else if (mCaptureType == CaptureType.ZOOM_AFTER_PICTURE_CAPTURE) {
            selectPhoto();
        }
    }

    /**
     * 选择图片
     */
    private void selectPhoto() {
        Intent intent = new Intent(Intent.ACTION_PICK);
        // 相片类型
        intent.setType("image/*");
        startActivityForResult(intent, PICTURE_CAPTURE);
    }

    /**
     * 拍照
     */
    private void takePhoto() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            // 下面是对调用相机拍照权限进行申请
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA,}, TAKE_PHOTO_PERMISSION_REQUEST_CODE);
            return;
        }
        // 创建File对象，用于存储拍照后的图片
        // 将此图片存储于SD卡的根目录下
        File outputImage = getTempImageFile();
        try {
            if (outputImage.exists()) {
                outputImage.delete();
            }
            outputImage.createNewFile();
        } catch (IOException e) {
            e.printStackTrace();
        }

        /**
         * 因 Android 7.0 开始，不能使用 file:// 类型的 Uri 访问跨应用文件，否则报异常，
         * 因此我们这里需要使用内容提供器，FileProvider 是 ContentProvider 的一个子类，
         * 我们可以轻松的使用 FileProvider 来在不同程序之间分享数据(相对于 ContentProvider 来说)
         */
        if (Build.VERSION.SDK_INT >= 24) {
            imageUri = FileProvider.getUriForFile(this, getPackageName() + ".photo.provider", outputImage);
        } else {
            // 将File对象转换成Uri对象
            // Uri表标识着图片的地址
            // Android 7.0 以前使用原来的方法来获取文件的 Uri
            imageUri = Uri.fromFile(outputImage);
        }

        mPreferences.
                edit()
                .putString("filepath", outputImage.getAbsolutePath())
                .apply();
        if (state.equals(Environment.MEDIA_MOUNTED)) {
            Intent getImageByCamera = new Intent("android.media.action.IMAGE_CAPTURE");
            getImageByCamera.putExtra(MediaStore.EXTRA_OUTPUT, imageUri);
            startActivityForResult(getImageByCamera, TAKE_PHOTO);
        } else {
            Toast.makeText(getApplicationContext(), R.string.base_hint_no_sd_card, Toast.LENGTH_LONG).show();
        }
    }

    /**
     * 临时图片文件
     *
     * @return File
     */
    private File getTempImageFile() {
        String filepath = mPreferences.getString("filepath", null);
        if (filepath == null) {
            File dir = new File(Environment.getExternalStorageDirectory().getPath() + "/temp");
            File outputImage = new File(dir, "tem.jpg");
            if (!dir.exists()) {
                dir.mkdirs();
            }
            mPreferences.
                    edit()
                    .putString("filepath", outputImage.getAbsolutePath())
                    .apply();

            return outputImage;
        } else {
            return new File(filepath);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        Log.d(TAG, "onActivityResult requestCode:" + requestCode + " resultCode:" + resultCode + " data:" + data);
        String filepath;
        if (resultCode == RESULT_OK) {
            switch (requestCode) {
                case TAKE_PHOTO:
                    filepath = mPreferences.getString("filepath", null);
                    imageUri = Uri.fromFile(new File(filepath));
                    if (mCaptureType == CaptureType.ZOOM_AFTER_TAKE_PHOTO) {
                        startPhotoZoom(imageUri, imageUri, ZOOM_AFTER_TAKE_PHOTO);
                    } else {
                        finishPage(resultCode, imageUri);
                    }
                    break;
                case PICTURE_CAPTURE:
                    if (mCaptureType == CaptureType.ZOOM_AFTER_PICTURE_CAPTURE) {
                        imageUri = Uri.fromFile(getTempImageFile());
                        startPhotoZoom(data.getData(), imageUri, ZOOM_AFTER_PICTURE_CAPTURE);
                    } else {
                        imageUri = data.getData();
                        finishPage(resultCode, imageUri);
                    }
                    break;
                case ZOOM_AFTER_TAKE_PHOTO:
                    // 拿到照相截取后的剪切数据
                    // Bitmap bmap = data.getParcelableExtra("data");
                    imageUri = Uri.fromFile(getTempImageFile());
                    finishPage(resultCode, imageUri);
                    break;
                case ZOOM_AFTER_PICTURE_CAPTURE:
                    // 拿到从相册选择截取后的剪切数据
                    finishPage(resultCode, imageUri);
                    break;
                default:
                    finish();
                    break;
            }
        } else {
            finish();
        }
    }

    private void finishPage(int resultCode, Uri imageUri) {
        Intent intent = new Intent();
        intent.setData(imageUri);
        setResult(resultCode, intent);
        finish();
    }

    /**
     * 通过uri获取bitmap
     */
    private Bitmap getBitmapFromUri(Uri uri) {
        try {
            // 读取uri所在的图片
            Bitmap bitmap = MediaStore.Images.Media.getBitmap(this.getContentResolver(), uri);
            return bitmap;
        } catch (Exception e) {
            Log.e(TAG, e.getMessage());
            Log.e(TAG, "目录为：" + uri);
            e.printStackTrace();
            return null;
        }
    }

    /**
     * 图片裁剪
     */
    private void startPhotoZoom(Uri uri, Uri targetUri, int i) {
        Log.d(TAG, "startPhotoZoom uri:" + uri);
        if (uri == null) {
            Toast.makeText(getApplicationContext(), "选择图片出错！", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        Intent intent = new Intent("com.android.camera.action.CROP");
        intent.setDataAndType(uri, "image/*");
        // 设置裁剪
        intent.putExtra("crop", "true");
        // aspectX aspectY 是宽高的比例
        intent.putExtra("aspectX", 1);
        intent.putExtra("aspectY", 1);
        // outputX outputY 是裁剪图片宽高
        intent.putExtra("outputX", outputX);
        intent.putExtra("outputY", outputY);
        // 如果为true,则通过 Bitmap bmap = data.getParcelableExtra("data")取出数据
        intent.putExtra("return-data", false);
        intent.putExtra(MediaStore.EXTRA_OUTPUT, targetUri);

        // 黑边
        intent.putExtra("scale", true)
                .putExtra("scaleUpIfNeeded", true);
        // 图片格式
        intent.putExtra("outputFormat", Bitmap.CompressFormat.JPEG.toString());
        // 取消人脸识别
        intent.putExtra("noFaceDetection", true);
        startActivityForResult(intent, i);
    }

    /**
     * 在这里进行用户权限授予结果处理
     *
     * @param requestCode  权限要求码，即我们申请权限时传入的常量
     * @param permissions  保存权限名称的 String 数组，可以同时申请一个以上的权限
     * @param grantResults 每一个申请的权限的用户处理结果数组(是否授权)
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch (requestCode) {
            // 调用相机拍照：
            case TAKE_PHOTO_PERMISSION_REQUEST_CODE:
                // 如果用户授予权限，那么打开相机拍照
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    takePhoto();
                } else {
                    Toast.makeText(this, "拍照权限被拒绝", Toast.LENGTH_SHORT).show();
                    finish();
                }
                break;
            // 打开相册选取：
            case WRITE_SDCARD_PERMISSION_REQUEST_CODE:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {

                } else {
                    Toast.makeText(this, "读写内存卡内容权限被拒绝", Toast.LENGTH_SHORT).show();
                    finish();
                }
                break;
            default:
                break;
        }
    }
}
