package mobile.util;


import com.github.unidbg.AndroidEmulator;
import com.github.unidbg.linux.android.AndroidARMEmulator;
import com.github.unidbg.linux.android.AndroidResolver;
import com.github.unidbg.linux.android.dvm.DalvikModule;
import com.github.unidbg.linux.android.dvm.DvmClass;
import com.github.unidbg.linux.android.dvm.VM;
import com.github.unidbg.linux.android.dvm.array.ByteArray;
import com.github.unidbg.memory.Memory;
import lombok.SneakyThrows;

import java.io.File;

public class TTEncrypt {
    public static final TTEncrypt ttEncrypt = new TTEncrypt(false);
    private final AndroidEmulator emulator;
    private final VM vm;
    private final com.github.unidbg.Module module;
    private final DvmClass TTEncryptUtils;
    private final boolean logging;

    public static synchronized byte[] getEn(byte[] data) {
        return ttEncrypt.ttEncrypt(data);
    }

    @SneakyThrows
    public TTEncrypt(boolean logging) {
        this.logging = logging;
        emulator = new AndroidARMEmulator("com.ss.awame.like");
        final Memory memory = emulator.getMemory();
        memory.setLibraryResolver(new AndroidResolver(23));
        vm = emulator.createDalvikVM(null);
        vm.setVerbose(false);
        DalvikModule dm = vm.loadLibrary(new File("src/main/resources/android/so/libEncryptor.so"), false);
        dm.callJNI_OnLoad(emulator);
        module = dm.getModule();
        TTEncryptUtils = vm.resolveClass("com/bytedance/frameworks/encryptor/EncryptorUtil");
    }

    public void destroy() {
        try {
            emulator.close();
            if (logging) {
                System.out.println("destroy");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

    }


    public byte[] ttEncrypt(byte[] data) {

        Number ret = TTEncryptUtils.callStaticJniMethod(emulator, "ttEncrypt([BI)[B", vm.addLocalObject(new ByteArray(data)), data.length);

        long hash = ret.intValue() & 0xffffffffL;
        ByteArray array = vm.getObject(hash);
        vm.deleteLocalRefs();
        return array.getValue();
    }

}
