package com.termux.shiroikuma.automation;

import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.content.pm.SigningInfo;
import android.os.Binder;
import android.os.Build;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Who is allowed through the automation data door ({@link AutomationProvider}), and how that is
 * decided. shiroikuma-termux (Phase 4b). Ported verbatim from 自由作業盤's {@code AutomationCallers.kt} via raikidoban — deliberately self-contained and
 * app-independent, so every sister app checks callers the same way.
 *
 * <h3>Why not a token</h3>
 *
 * The token this replaces was a 48-character secret 白い熊 pasted from one app's settings into
 * another's. It cannot survive a wipe, which is fatal for the case the whole family now exists to
 * serve: 応用管理 restoring apps and their data onto a clean phone, where nothing is configured yet.
 *
 * <h3>Why not a {@code shiroikuma.*} prefix</h3>
 *
 * Because that is not an identity. What makes {@code getCallingPackage()} worth anything is that a
 * package name <b>cannot be taken while the real package is installed</b> — package names are not a
 * namespace anyone owns, so any sideloaded app may call itself {@code shiroikuma.evil} and pass a
 * prefix test. Since the caller supplies the file descriptor an export is written into, a prefix
 * check would hand such an app the complete data of every sister app in turn: <b>strictly weaker
 * than the token it replaces</b>.
 *
 * <h3>What is actually checked, in order</h3>
 *
 * <ol>
 * <li><b>An exact name</b> from {@link #CALLERS}. Never a prefix. A third caller is a one-line
 * change here.</li>
 * <li><b>The uid agrees.</b> {@code getCallingPackage()} reflects the caller's <i>declared</i>
 * attribution, and packages sharing a uid are not distinguished by it, so it is confirmed against
 * {@link PackageManager#getPackagesForUid} for the uid the kernel reports. That answer cannot be
 * borrowed.</li>
 * <li><b>The signing certificate matches a pinned hash.</b> This closes the real gap: <i>whichever
 * caller package is absent from the device is a name anyone can take</i>, and a clean phone is
 * precisely a device where not everything is installed yet — the moment the assumption is weakest is
 * the moment it is most needed.</li>
 * </ol>
 */
public final class AutomationCallers {

    /**
     * The apps allowed to drive this app's data door.
     *
     * <p>応用管理 backs up and restores; 自由作業盤 runs the 保存復元 batch. Nothing else has any
     * business exporting this app's data, and an entry added here is a deliberate act.
     *
     * <p>Where the hashes come from, so the next person can re-derive them rather than trust them:
     * <pre>apksigner verify --print-certs &lt;the app's signed release APK&gt; | grep 'SHA-256 digest'</pre>
     * Every app in the family has <b>its own keystore</b>, so there is no shared signing key to
     * compare against and each caller must be pinned by name — which is also why a
     * {@code protectionLevel="signature"} permission was never an option here.
     *
     * <p><b>If a caller's key is ever rotated, its calls stop working and the fix is these
     * constants.</b> That is the intended failure: a signing key changing without anyone noticing is
     * exactly what a pin exists to catch.
     */
    private static final Map<String, String> CALLERS = new HashMap<>();

    static {
        CALLERS.put("shiroikuma.oyokanri",
                "9c585f4d118cb97ff653f949a8872875548403b9083ce6b9baa2e8f0c55ac6cc");
        CALLERS.put("shiroikuma.jiyusagyoban",
                "efd0d352192651593a92288ecdc64fc87262ec8648c24ed8f51a5587d46ac602");
    }

    private AutomationCallers() {
    }

    /**
     * {@code null} when the caller may proceed, otherwise the exact {@code ERROR:} line to answer
     * with.
     *
     * <p>It answers a string and not a boolean because a refusal that says only "no" is a refusal
     * nobody can debug from the other side of an IPC boundary. Each of these is a different mistake
     * with a different fix, and the caller shows them to 白い熊 verbatim.
     *
     * <p>Must be called from inside the binder transaction — {@link Binder#getCallingUid()} is only
     * meaningful there.
     */
    public static String verify(Context context, String declared) {
        if (declared == null || declared.isEmpty()) {
            return "ERROR:caller unknown";
        }
        String pin = CALLERS.get(declared);
        if (pin == null) {
            return "ERROR:caller not permitted: " + declared;
        }

        // The kernel's answer, not the caller's. A package may declare an attribution it does not
        // own; the uid cannot be borrowed.
        String[] real;
        try {
            real = context.getPackageManager().getPackagesForUid(Binder.getCallingUid());
        } catch (Exception e) {
            real = null;
        }
        if (real == null || !Arrays.asList(real).contains(declared)) {
            return "ERROR:caller uid mismatch: " + declared;
        }

        String signature = signingSha256(context, declared);
        if (signature == null) {
            return "ERROR:caller signature unreadable: " + declared;
        }
        // Constant-time, like the token compare it replaces — the value is a public hash, but the
        // habit is worth keeping and costs nothing.
        if (!MessageDigest.isEqual(signature.getBytes(StandardCharsets.UTF_8),
                pin.getBytes(StandardCharsets.UTF_8))) {
            return "ERROR:caller signature mismatch: " + declared;
        }
        return null;
    }

    /**
     * The SHA-256 of the caller's current signing certificate, lower-case hex.
     *
     * <p>{@code signingInfo} rather than the deprecated {@code signatures}: a rotated key reports its
     * whole history and we want the certificate actually in force. {@code GET_SIGNING_CERTIFICATES}
     * is API 28 and this module's {@code minSdk} is 21 — on a 21–27 device the flag is accepted and
     * {@code signingInfo} comes back null, so WITHOUT the older branch the door would refuse every
     * caller: a total failure that never appears on 白い熊's phone (API 31) and would only surface on
     * an older one. The deprecated array is the correct answer there, not a compromise — before key
     * rotation existed, {@code signatures} <i>was</i> the signing certificate.
     *
     * <p>An app with more than one current signer is refused by returning null: "several signers, one
     * of which matches" is a question about key rotation that nothing in this family needs to answer.
     */
    @SuppressWarnings("deprecation")
    private static String signingSha256(Context context, String pkg) {
        try {
            PackageManager pm = context.getPackageManager();
            Signature[] certs;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                SigningInfo info = pm.getPackageInfo(pkg, PackageManager.GET_SIGNING_CERTIFICATES)
                        .signingInfo;
                certs = info == null ? null : info.getApkContentsSigners();
            } else {
                certs = pm.getPackageInfo(pkg, PackageManager.GET_SIGNATURES).signatures;
            }
            if (certs == null || certs.length != 1) {
                return null;
            }
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(certs[0].toByteArray());
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (Exception e) {
            return null;
        }
    }
}
