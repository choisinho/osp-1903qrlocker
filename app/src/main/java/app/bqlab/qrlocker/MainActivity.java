package app.bqlab.qrlocker;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.support.annotation.Nullable;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.InputType;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.Toast;

import java.util.Objects;

import app.akexorcist.bluetotohspp.library.BluetoothSPP;
import app.akexorcist.bluetotohspp.library.BluetoothState;

public class MainActivity extends AppCompatActivity {

    //constants
    final String LOCKER_OPEN = "1";
    final String LOCKER_LOCK = "2";
    //variables
    boolean master;
    boolean deviceConnected;
    String deviceAddress;
    //objects
    BluetoothSPP mBluetooth;
    SharedPreferences mKeyPref;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        init();
        connectDevice();
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
                if (deviceConnected)
                    Toast.makeText(MainActivity.this, "이미 장치와 연결되어 있습니다.", Toast.LENGTH_LONG).show();
                else
                    startActivity(new Intent(MainActivity.this, ScanActivity.class));
            }
        });
        findViewById(R.id.main_state).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String state = deviceConnected ? "연결됨" : "연결되지 않음";
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
                if (deviceConnected) {
                    final EditText passwordInput = new EditText(MainActivity.this);
                    passwordInput.setImeOptions(EditorInfo.IME_ACTION_DONE);
                    passwordInput.setInputType(InputType.TYPE_CLASS_NUMBER);
                    passwordInput.setSingleLine();
                    new AlertDialog.Builder(MainActivity.this)
                            .setMessage("설정할 비밀번호를 입력하세요.")
                            .setView(passwordInput)
                            .setNegativeButton("취소", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {

                                }
                            })
                            .setPositiveButton("확인", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    mKeyPref.edit().putString(deviceAddress, passwordInput.getText().toString()).apply();
                                    Toast.makeText(MainActivity.this, "설정이 완료되었습니다.", Toast.LENGTH_LONG).show();
                                }
                            }).show();
                } else
                    Toast.makeText(MainActivity.this, "장치와 연결되지 않았습니다.", Toast.LENGTH_LONG).show();
            }
        });
        findViewById(R.id.main_master).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new AlertDialog.Builder(MainActivity.this)
                        .setMessage("마스터 키를 사용할까요?")
                        .setPositiveButton("취소", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {

                            }
                        })
                        .setPositiveButton("확인", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                if (!master) {
                                    Toast.makeText(MainActivity.this, "마스터 키를 사용합니다.", Toast.LENGTH_LONG).show();
                                    master = true;
                                } else
                                    Toast.makeText(MainActivity.this, "이미 마스터 키를 사용하고 있습니다.", Toast.LENGTH_LONG).show();
                            }
                        })
                        .setNeutralButton("마스터 키를 사용하지 않음", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                Toast.makeText(MainActivity.this, "마스터 키를 사용하지 않습니다.", Toast.LENGTH_LONG).show();
                                master = false;
                            }
                        })
                        .show();
            }
        });
    }

    private void connectDevice() {
        deviceAddress = getIntent().getStringExtra("DEVICE_ADDRESS");
        if (deviceAddress != null) {
            if (!mBluetooth.isBluetoothAvailable()) {
                Toast.makeText(this, "블루투스를 지원하지 않는 장치입니다.", Toast.LENGTH_LONG).show();
                finishAffinity();
            } else if (!mBluetooth.isBluetoothEnabled()) {
                startActivityForResult(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE), BluetoothState.REQUEST_ENABLE_BT);
            } else if (!mBluetooth.isServiceAvailable()) {
                mBluetooth.setupService();
                mBluetooth.startService(BluetoothState.DEVICE_OTHER);
                connectDevice();
            } else {
                mBluetooth.setBluetoothConnectionListener(new BluetoothSPP.BluetoothConnectionListener() {
                    @Override
                    public void onDeviceConnected(String name, final String address) {
                        deviceConnected = true;
                        if (master) {
                            mBluetooth.send(LOCKER_OPEN, true);
                            Toast.makeText(MainActivity.this, "잠금이 해제되었습니다.", Toast.LENGTH_LONG).show();
                        } else {
                            if (Objects.equals(mKeyPref.getString(address, ""), "")) {
                                final EditText passwordInput = new EditText(MainActivity.this);
                                passwordInput.setImeOptions(EditorInfo.IME_ACTION_DONE);
                                passwordInput.setInputType(InputType.TYPE_CLASS_NUMBER);
                                passwordInput.setSingleLine();
                                new AlertDialog.Builder(MainActivity.this)
                                        .setMessage("설정할 비밀번호를 입력하세요.")
                                        .setView(passwordInput)
                                        .setNegativeButton("취소", new DialogInterface.OnClickListener() {
                                            @Override
                                            public void onClick(DialogInterface dialog, int which) {

                                            }
                                        })
                                        .setPositiveButton("확인", new DialogInterface.OnClickListener() {
                                            @Override
                                            public void onClick(DialogInterface dialog, int which) {
                                                mKeyPref.edit().putString(address, passwordInput.getText().toString()).apply();
                                                Toast.makeText(MainActivity.this, "설정이 완료되었습니다.", Toast.LENGTH_LONG).show();
                                            }
                                        }).show();
                            } else {
                                final EditText passwordInput = new EditText(MainActivity.this);
                                passwordInput.setImeOptions(EditorInfo.IME_ACTION_DONE);
                                passwordInput.setInputType(InputType.TYPE_CLASS_NUMBER);
                                passwordInput.setSingleLine();
                                new AlertDialog.Builder(MainActivity.this)
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
                                                if (Objects.equals(mKeyPref.getString(address, ""), passwordInput.getText().toString())) {
                                                    mBluetooth.send(LOCKER_OPEN, true);
                                                    Toast.makeText(MainActivity.this, "잠금이 해제되었습니다.", Toast.LENGTH_LONG).show();
                                                }
                                            }
                                        }).show();
                            }
                        }
                    }

                    @Override
                    public void onDeviceDisconnected() {
                        deviceConnected = false;
                        Log.d("MainActivity", "Disconnected: " + deviceAddress);
                        Toast.makeText(MainActivity.this, "장치와의 연결이 끊겼습니다.", Toast.LENGTH_LONG).show();
                    }

                    @Override
                    public void onDeviceConnectionFailed() {
                        deviceConnected = false;
                        Log.d("MainActivity", "Fail to connect: " + deviceAddress);
                        Toast.makeText(MainActivity.this, "장치와 연결할 수 없습니다.", Toast.LENGTH_LONG).show();
                    }
                });
                mBluetooth.connect(deviceAddress);
            }
        }
    }
}
