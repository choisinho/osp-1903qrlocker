package app.bqlab.qrlocker;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextPaint;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import java.util.Objects;

public class MainActivity extends AppCompatActivity {

    //constants
    final String LOCKER_OPEN = "1";
    final String LOCKER_LOCK = "2";
    //variables
    boolean connectFlag;
    //objects
    BluetoothSPP mBluetooth;
    SharedPreferences mKeyPref, mSetting;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        init();
        connectDevice();
        requestPermission();
        startService(new Intent(this, TaskService.class));
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        if (resultCode == Activity.RESULT_CANCELED) {
            switch (requestCode) {
                case BluetoothState.REQUEST_ENABLE_BT:
                    Toast.makeText(this, "블루투스를 사용할 수 없습니다.", Toast.LENGTH_LONG).show();
                    break;
            }
        } else {
            switch (requestCode) {
                case BluetoothState.REQUEST_ENABLE_BT:
                    connectDevice();
                    break;
            }
        }
    }

    private void init() {
        //initialize
        mBluetooth = new BluetoothSPP(this);
        mKeyPref = getSharedPreferences("KEYS", MODE_PRIVATE);
        mSetting = getSharedPreferences("SETTING", MODE_PRIVATE);
        //events
        findViewById(R.id.main_scan).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, ScanActivity.class));
                finish();
            }
        });
        findViewById(R.id.main_state).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String state = mSetting.getBoolean("DEVICE_CONNECTED", false) ? "연결됨" : "연결되지 않음";
                new AlertDialog.Builder(MainActivity.this)
                        .setMessage("블루투스 상태: " + state)
                        .setPositiveButton("확인", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {

                            }
                        }).show();
            }
        });
        findViewById(R.id.main_master).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new AlertDialog.Builder(MainActivity.this)
                        .setMessage("마스터 키를 사용할까요?")
                        .setNegativeButton("취소", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {

                            }
                        })
                        .setPositiveButton("확인", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                if (!mSetting.getBoolean("MASTER", false)) {
                                    mSetting.edit().putBoolean("MASTER", true).apply();
                                    Toast.makeText(MainActivity.this, "마스터 키를 사용합니다.", Toast.LENGTH_LONG).show();
                                } else
                                    Toast.makeText(MainActivity.this, "이미 마스터 키를 사용하고 있습니다.", Toast.LENGTH_LONG).show();
                            }
                        })
                        .setNeutralButton("마스터 키 해제", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                mSetting.edit().putBoolean("MASTER", false).apply();
                                Toast.makeText(MainActivity.this, "마스터 키를 사용하지 않습니다.", Toast.LENGTH_LONG).show();
                            }
                        })
                        .show();
            }
        });
    }

    private void connectDevice() {
        if (!mBluetooth.isBluetoothAvailable()) {
            Toast.makeText(this, "블루투스를 지원하지 않는 기기입니다.", Toast.LENGTH_LONG).show();
            Log.d("MainActivity", "Bluetooth not supported");
            finishAffinity();
        } else if (!mBluetooth.isBluetoothEnabled()) {
            startActivityForResult(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE), BluetoothState.REQUEST_ENABLE_BT);
            Log.d("MainActivity", "Bluetooth be enabled");
        } else if (!mBluetooth.isServiceAvailable()) {
            mBluetooth.setupService();
            mBluetooth.startService(BluetoothState.DEVICE_OTHER);
            Log.d("MainActivity", "Bluetooth installed");
            connectDevice();
        } else {
            if (!Objects.equals(mSetting.getString("DEVICE_ADDRESS", ""), "")) {
                mBluetooth.setOnDataReceivedListener(new BluetoothSPP.OnDataReceivedListener() {
                    @Override
                    public void onDataReceived(byte[] data, String message) {
                        mSetting.edit().putBoolean("DEVICE_CONNECTED", true).apply();
                        if (mSetting.getBoolean("DEVICE_CONNECTED", false) && !connectFlag) {
                            connectFlag = true;
                            controlLocker();
                        }
                    }
                });
                mBluetooth.setBluetoothConnectionListener(new BluetoothSPP.BluetoothConnectionListener() {
                    @Override
                    public void onDeviceConnected(String name, String address) {
                        Toast.makeText(MainActivity.this, "사물함과 연결하는 중입니다.", Toast.LENGTH_LONG).show();
                    }

                    @Override
                    public void onDeviceDisconnected() {
                        mSetting.edit().putBoolean("DEVICE_CONNECTED", false).apply();
                    }

                    @Override
                    public void onDeviceConnectionFailed() {
                        mSetting.edit().putBoolean("DEVICE_CONNECTED", false).apply();
                    }
                });
                mBluetooth.connect(mSetting.getString("DEVICE_ADDRESS", ""));
            }
        }
    }

    private void controlLocker() {
        if (isPasswordSet()) {
            //user want to locker open
            if (mSetting.getBoolean("MASTER", false)) {
                //if user has master key
                try {
                    mBluetooth.send(LOCKER_OPEN, false);
                    Thread.sleep(500);
                    mBluetooth.send(LOCKER_OPEN, false);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                Toast.makeText(MainActivity.this, "사물함이 열립니다.", Toast.LENGTH_LONG).show();
            } else {
                showCheckPasswordDialog();
            }
        } else {
            //user want to locker set and close
            try {
                mBluetooth.send(LOCKER_OPEN, false);
                Thread.sleep(500);
                mBluetooth.send(LOCKER_OPEN, false);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            Toast.makeText(MainActivity.this, "사물함이 열립니다.", Toast.LENGTH_LONG).show();
            showSetPasswordDialog();
        }
    }

    private void showSetPasswordDialog() {
        final EditText passwordInput = new EditText(this);
        passwordInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        passwordInput.setSingleLine();
        new AlertDialog.Builder(this)
                .setCancelable(false)
                .setTitle("사물함 이용")
                .setMessage("물건을 넣은 후 비밀번호를 입력하면 사물함이 닫힙니다.")
                .setView(passwordInput)
                .setPositiveButton("확인", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String input = passwordInput.getText().toString();
                        if (input.isEmpty()) {
                            try {
                                Toast.makeText(MainActivity.this, "비밀번호를 입력하지 않았습니다.", Toast.LENGTH_LONG).show();
                                mBluetooth.send(LOCKER_LOCK, false);
                                Thread.sleep(500);
                                mBluetooth.send(LOCKER_LOCK, false);
                                Thread.sleep(500);
                                mBluetooth.disconnect();
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        } else {
                            try {
                                mBluetooth.send(LOCKER_LOCK, false);
                                Thread.sleep(500);
                                mBluetooth.send(LOCKER_LOCK, false);
                                Thread.sleep(500);
                                mBluetooth.disconnect();
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                            Toast.makeText(MainActivity.this, "비밀번호 설정이 완료되었습니다.", Toast.LENGTH_LONG).show();
                            mKeyPref.edit().putString(mSetting.getString("DEVICE_ADDRESS", ""), input).apply();
                        }
                    }
                }).show();
    }

    private void showCheckPasswordDialog() {
        final EditText passwordInput = new EditText(this);
        passwordInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        passwordInput.setSingleLine();
        new AlertDialog.Builder(this)
                .setCancelable(false)
                .setTitle("사물함 설정")
                .setMessage("설정한 비밀번호를 입력하세요.")
                .setView(passwordInput)
                .setPositiveButton("확인", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String input = passwordInput.getText().toString();
                        if (isPasswordRight(input) && !input.isEmpty()) {
                            new AlertDialog.Builder(MainActivity.this)
                                    .setTitle("사물함 설정")
                                    .setMessage("사물함을 열어 물건을 꺼낼 지 비밀번호를 변경할 지 고르세요.")
                                    .setCancelable(false)
                                    .setNegativeButton("취소", new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialog, int which) {
                                            Toast.makeText(MainActivity.this, "설정을 취소하였습니다.", Toast.LENGTH_LONG).show();
                                            mBluetooth.disconnect();
                                        }
                                    }).setPositiveButton("사물함 열기", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    try {
                                        mBluetooth.send(LOCKER_OPEN, false);
                                        Thread.sleep(500);
                                        mBluetooth.send(LOCKER_OPEN, false);
                                    } catch (InterruptedException e) {
                                        e.printStackTrace();
                                    }
                                    new AlertDialog.Builder(MainActivity.this)
                                            .setCancelable(false)
                                            .setTitle("사물함 설정")
                                            .setMessage("물건을 꺼내신 후 사물함을 닫으세요.")
                                            .setPositiveButton("사물함 닫기", new DialogInterface.OnClickListener() {
                                                @Override
                                                public void onClick(DialogInterface dialog, int which) {
                                                    mKeyPref.edit().remove(mSetting.getString("DEVICE_ADDRESS", "")).apply();
                                                    mKeyPref.edit().putString(mSetting.getString("DEVICE_ADDRESS", ""), "").apply();
                                                    Toast.makeText(MainActivity.this, "이용해주셔서 감사합니다.", Toast.LENGTH_LONG).show();
                                                    try {
                                                        mBluetooth.send(LOCKER_LOCK, false);
                                                        Thread.sleep(500);
                                                        mBluetooth.send(LOCKER_LOCK, false);
                                                        Thread.sleep(500);
                                                        mBluetooth.disconnect();
                                                    } catch (InterruptedException e) {
                                                        e.printStackTrace();
                                                    }
                                                }
                                            }).show();
                                }
                            }).setNeutralButton("비밀번호 재설정", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    final EditText passwordInput = new EditText(MainActivity.this);
                                    passwordInput.setInputType(InputType.TYPE_CLASS_NUMBER);
                                    passwordInput.setSingleLine();
                                    new AlertDialog.Builder(MainActivity.this)
                                            .setTitle("사물함 설정")
                                            .setMessage("재설정할 비밀번호를 입력하세요.")
                                            .setCancelable(false)
                                            .setView(passwordInput)
                                            .setPositiveButton("확인", new DialogInterface.OnClickListener() {
                                                @Override
                                                public void onClick(DialogInterface dialog, int which) {
                                                    String input = passwordInput.getText().toString();
                                                    if (input.isEmpty()) {
                                                        Toast.makeText(MainActivity.this, "비밀번호를 입력하지 않았습니다.", Toast.LENGTH_LONG).show();
                                                        mBluetooth.disconnect();
                                                    } else {
                                                        Toast.makeText(MainActivity.this, "비밀번호 설정이 완료되었습니다.", Toast.LENGTH_LONG).show();
                                                        mKeyPref.edit().putString(mSetting.getString("DEVICE_ADDRESS", ""), input).apply();
                                                        Toast.makeText(MainActivity.this, "설정이 완료되었습니다.", Toast.LENGTH_LONG).show();
                                                        mBluetooth.disconnect();
                                                    }
                                                }
                                            }).show();
                                }
                            }).show();
                        } else {
                            Toast.makeText(MainActivity.this, "비밀번호가 틀립니다.", Toast.LENGTH_LONG).show();
                            mBluetooth.disconnect();
                        }
                    }
                }).show();
    }

    private boolean isPasswordSet() {
        return !Objects.equals(mKeyPref.getString(mSetting.getString("DEVICE_ADDRESS", ""), ""), "");
    }

    private boolean isPasswordRight(String input) {
        String original = mKeyPref.getString(mSetting.getString("DEVICE_ADDRESS", ""), "");
        Log.d("MainActivity", "isPasswordRight: Origianl: " + original);
        Log.d("MainActivity", "isPasswordRight: Inputted: " + input);
        return Objects.equals(original, input);
    }

    private void requestPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, 0);
        }
    }
}
