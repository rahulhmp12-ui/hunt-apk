package ai.venice.hunt;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

public class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Intent i = new Intent();
        i.setClassName("com.termux", "com.termux.app.RunCommandService");
        i.setAction("com.termux.RUN_COMMAND");
        i.putExtra("com.termux.RUN_COMMAND_PATH",
                "/data/data/com.termux/files/usr/bin/hunt");
        i.putExtra("com.termux.RUN_COMMAND_ARGUMENTS", new String[]{});
        i.putExtra("com.termux.RUN_COMMAND_WORKDIR",
                "/data/data/com.termux/files/home");
        i.putExtra("com.termux.RUN_COMMAND_BACKGROUND", false);
        i.putExtra("com.termux.RUN_COMMAND_SESSION_ACTION", "0");
        try {
            startService(i);
        } catch (Exception e) {
            Toast.makeText(this, "Termux paoa jayni: " + e.getMessage(),
                    Toast.LENGTH_LONG).show();
        }
        finish();
    }
}
