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
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import java.util.Objects;

public class MainActivity extends AppCompatActivity {

    //constants
    final String LOCKER_OPEN = "1";
    final String LOCKER_LOCK = "2";
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
    }

    @Override
    protected void onResume() {
        super.onResume();
        startActivity(getIntent());
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
        //events
        findViewById(R.id.main_scan).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, ScanActivity.class));
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
        findViewById(R.id.main_password).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mSetting.getBoolean("DEVICE_CONNECTED", false))
                    showEditPasswordDialog();
                else
                    Toast.makeText(MainActivity.this, "사물함과 연결되어 있지 않습니다.", Toast.LENGTH_LONG).show();
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
        if (Objects.equals(mSetting.getString("DEVICE_ADDRESS", ""), "")) {
            if (mSetting.getBoolean("DEVICE_CONNECTED", false)) {
                showCheckPasswordDialog();
            } else {
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
                    mBluetooth.setOnDataReceivedListener(new BluetoothSPP.OnDataReceivedListener() {
                        @Override
                        public void onDataReceived(byte[] data, String message) {
                            mSetting.edit().putBoolean("DEVICE_CONNECTED", true).apply();
                        }
                    });
                    mBluetooth.setBluetoothConnectionListener(new BluetoothSPP.BluetoothConnectionListener() {
                        @Override
                        public void onDeviceConnected(String name, final String address) {
                            Toast.makeText(MainActivity.this, "연결되었습니다.", Toast.LENGTH_LONG).show();
                            if (!isPasswordSet()) {
                                try {
                                    mBluetooth.send(LOCKER_OPEN, true);
                                    Thread.sleep(500);
                                    mBluetooth.send(LOCKER_OPEN, true);
                                } catch (InterruptedException e) {
                                    e.printStackTrace();
                                }
                                showSetPasswordDialog();
                            } else
                                showCheckPasswordDialog();
                        }

                        @Override
                        public void onDeviceDisconnected() {
                            mSetting.edit().putBoolean("DEVICE_CONNECTED", false).apply();
                        }

                        @Override
                        public void onDeviceConnectionFailed() {
                            mSetting.edit().putBoolean("DEVICE_CONNECTED", false).apply();
                            Toast.makeText(MainActivity.this, "사물함과 연결할 수 없습니다.", Toast.LENGTH_LONG).show();
                        }
                    });
                    try {
                        mBluetooth.connect(mSetting.getString("DEVICE_ADDRESS", ""));
                        Toast.makeText(this, "사물함과 연결하는 중입니다.", Toast.LENGTH_LONG).show();
                    } catch (IllegalArgumentException e) {
                        Log.d("MainActivity", "IllegalArgumentException");
                        Toast.makeText(MainActivity.this, "잘못된 형식의 QR 코드입니다.", Toast.LENGTH_LONG).show();
                    }
                }
            }
        }
    }

    private void showSetPasswordDialog() {
        final EditText passwordInput = new EditText(this);
        passwordInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        passwordInput.setSingleLine();
        new AlertDialog.Builder(this)
                .setCancelable(false)
                .setTitle("잠금 설정")
                .setMessage("비밀번호를 설정하세요.")
                .setView(passwordInput)
                .setPositiveButton("확인", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (mSetting.getBoolean("DEVICE_CONNECTED", false)) {
                            String input = passwordInput.getText().toString();
                            if (input.isEmpty()) {
                                Toast.makeText(MainActivity.this, "비밀번호를 입력하세요.", Toast.LENGTH_LONG).show();
                            } else {
                                try {
                                    mBluetooth.send(LOCKER_LOCK, true);
                                    Thread.sleep(500);
                                    mBluetooth.send(LOCKER_LOCK, true);
                                } catch (InterruptedException e) {
                                    e.printStackTrace();
                                }
                                mSetting.edit().putString("DEVICE_ADDRESS", "").apply();
                                Log.d("MainActivity", mSetting.getString("DEVICE_ADDRESS", "") + "'s password set to " + input);
                            }
                        } else {
                            Log.d("MainActivity", "Fail to set password");
                            Toast.makeText(MainActivity.this, "사물함과 연결되어 있지 않습니다.", Toast.LENGTH_LONG).show();
                        }
                    }
                })
                .setOnDismissListener(new DialogInterface.OnDismissListener() {
                    @Override
                    public void onDismiss(DialogInterface dialog) {
                        if (!isPasswordSet()) {
                            Toast.makeText(MainActivity.this, "사물함을 사용하지 않습니다.", Toast.LENGTH_LONG).show();
                            Log.d("MainActivity", "Not input password");
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
                .setTitle("잠금 해제")
                .setMessage("설정한 비밀번호를 입력하세요.")
                .setView(passwordInput)
                .setPositiveButton("확인", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (mSetting.getBoolean("DEVICE_CONNECTED", false)) {
                            if (mSetting.getBoolean("MASTER", false)) {
                                try {
                                    Toast.makeText(MainActivity.this, "잠금이 해제됩니다.", Toast.LENGTH_LONG).show();
                                    mKeyPref.edit().putString(mSetting.getString("DEVICE_ADDRESS", ""), "").apply();
                                    mBluetooth.send(LOCKER_OPEN, true);
                                    Thread.sleep(500);
                                    mBluetooth.send(LOCKER_OPEN, true);
                                } catch (InterruptedException e) {
                                    e.printStackTrace();
                                }
                            } else {
                                String input = passwordInput.getText().toString();
                                if (isPasswordRight(input)) {
                                    try {
                                        Toast.makeText(MainActivity.this, "잠금이 해제됩니다.", Toast.LENGTH_LONG).show();
                                        mKeyPref.edit().putString(mSetting.getString("DEVICE_ADDRESS", ""), "").apply();
                                        mBluetooth.send(LOCKER_OPEN, true);
                                        Thread.sleep(500);
                                        mBluetooth.send(LOCKER_OPEN, true);
                                    } catch (InterruptedException e) {
                                        e.printStackTrace();
                                    }
                                } else {
                                    Toast.makeText(MainActivity.this, "비밀번호가 틀립니다.", Toast.LENGTH_LONG).show();
                                    Log.d("MainActivity", "Wrong password input");
                                }
                            }
                        } else {
                            Log.d("MainActivity", "Fail to set password");
                            Toast.makeText(MainActivity.this, "사물함과 연결되어 있지 않습니다.", Toast.LENGTH_LONG).show();
                        }
                    }
                }).show();
    }

    private void showEditPasswordDialog() {
        final EditText passwordInput = new EditText(this);
        passwordInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        passwordInput.setSingleLine();
        new AlertDialog.Builder(this)
                .setTitle("비밀번호 변경")
                .setMessage("설정한 비밀번호를 입력하세요.")
                .setView(passwordInput)
                .setNegativeButton("취소", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {

                    }
                })
                .setPositiveButton("확인", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (mSetting.getBoolean("DEVICE_CONNECTED", false)) {
                            if (mSetting.getBoolean("MASTER", false)) {
                                Toast.makeText(MainActivity.this, "마스터 키를 사용중입니다.", Toast.LENGTH_LONG).show();
                            } else {
                                String input = passwordInput.getText().toString();
                                if (input.isEmpty()) {
                                    Toast.makeText(MainActivity.this, "비밀번호를 입력하세요.", Toast.LENGTH_LONG).show();
                                } else {
                                    if (isPasswordRight(input)) {
                                        final String newInput = passwordInput.getText().toString();
                                        new AlertDialog.Builder(MainActivity.this)
                                                .setTitle("비밀번호 변경")
                                                .setMessage("변경할 비밀번호를 입력하세요.")
                                                .setView(passwordInput)
                                                .setNegativeButton("취소", new DialogInterface.OnClickListener() {
                                                    @Override
                                                    public void onClick(DialogInterface dialog, int which) {

                                                    }
                                                })
                                                .setPositiveButton("확인", new DialogInterface.OnClickListener() {
                                                    @Override
                                                    public void onClick(DialogInterface dialog, int which) {
                                                        mKeyPref.edit().putString(mSetting.getString("DEVICE_ADDRESS", ""), newInput).apply();
                                                        Log.d("MainActivity", mSetting.getString("DEVICE_ADDRESS", "") + "'s password edited to" + newInput);
                                                    }
                                                }).show();
                                    } else {
                                        Log.d("MainActivity", "Wrong password input");
                                        Toast.makeText(MainActivity.this, "비밀번호가 틀립니다.", Toast.LENGTH_LONG).show();
                                    }
                                }
                            }
                        } else {
                            Log.d("MainActivity", "Fail to set password");
                            Toast.makeText(MainActivity.this, "사물함과 연결되어 있지 않습니다.", Toast.LENGTH_LONG).show();
                        }
                    }
                }).show();
    }

    private boolean isPasswordSet() {
        return !Objects.equals(mKeyPref.getString(mSetting.getString("DEVICE_ADDRESS", ""), ""), "");
    }

    private boolean isPasswordRight(String input) {
        return Objects.equals(mKeyPref.getString(mSetting.getString("DEVICE_ADDRESS", ""), ""), input);
    }

    private void requestPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, 0);
        }
    }
}
