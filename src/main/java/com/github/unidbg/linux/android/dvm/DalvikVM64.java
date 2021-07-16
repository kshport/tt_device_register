package com.github.unidbg.linux.android.dvm;

import com.github.unidbg.Emulator;
import com.github.unidbg.arm.Arm64Svc;
import com.github.unidbg.arm.context.Arm64RegisterContext;
import com.github.unidbg.arm.context.EditableArm64RegisterContext;
import com.github.unidbg.arm.context.RegisterContext;
import com.github.unidbg.linux.android.dvm.array.ArrayObject;
import com.github.unidbg.linux.android.dvm.array.ByteArray;
import com.github.unidbg.linux.android.dvm.array.DoubleArray;
import com.github.unidbg.linux.android.dvm.array.IntArray;
import com.github.unidbg.memory.MemoryBlock;
import com.github.unidbg.memory.SvcMemory;
import com.github.unidbg.pointer.UnicornPointer;
import com.github.unidbg.utils.Inspector;
import com.sun.jna.Pointer;
import net.dongliu.apk.parser.ApkFile;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import unicorn.Arm64Const;
import unicorn.Unicorn;
import unicorn.UnicornException;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public class DalvikVM64 extends BaseVM implements VM {

    private static final Log log = LogFactory.getLog(DalvikVM64.class);

    private final UnicornPointer _JavaVM;
    private final UnicornPointer _JNIEnv;

    public DalvikVM64(Emulator<?> emulator, File apkFile) {
        super(emulator, apkFile);

        final SvcMemory svcMemory = emulator.getSvcMemory();
        _JavaVM = svcMemory.allocate(emulator.getPointerSize(), "_JavaVM");

        Pointer _FindClass = svcMemory.registerSvc(new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                Arm64RegisterContext context = emulator.getContext();
                Pointer env = context.getXPointer(0);
                Pointer className = context.getXPointer(1);
                String name = className.getString(0);

                if (verbose) {
                    System.out.println(String.format("JNIEnv->FindClass(%s) was called from %s", name, UnicornPointer.register(emulator, Arm64Const.UC_ARM64_REG_LR)));
                }

                if (notFoundClassSet.contains(name)) {
                    throwable = resolveClass("java/lang/NoClassDefFoundError").newObject(name);
                    return 0;
                }

                DvmClass dvmClass = resolveClass(name);
                long hash = dvmClass.hashCode() & 0xffffffffL;
                if (log.isDebugEnabled()) {
                    log.debug("FindClass env=" + env + ", className=" + name + ", hash=0x" + Long.toHexString(hash));
                }
                return hash;
            }
        });

        Pointer _ToReflectedMethod = svcMemory.registerSvc(new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                RegisterContext context = emulator.getContext();
                UnicornPointer clazz = context.getPointerArg(1);
                UnicornPointer jmethodID = context.getPointerArg(2);
                DvmClass dvmClass = classMap.get(clazz.toUIntPeer());
                DvmMethod dvmMethod = null;
                if (dvmClass != null) {
                    dvmMethod = dvmClass.getStaticMethod(jmethodID.toUIntPeer());
                    if (dvmMethod == null) {
                        dvmMethod = dvmClass.getMethod(jmethodID.toUIntPeer());
                    }
                }
                if (log.isDebugEnabled()) {
                    log.debug("ToReflectedMethod clazz=" + dvmClass + ", jmethodID=" + jmethodID + ", lr=" + context.getLRPointer());
                }
                if (dvmMethod == null) {
                    throw new UnicornException();
                } else {
                    if (verbose) {
                        System.out.println(String.format("JNIEnv->ToReflectedMethod(%s, \"%s\", %s) was called from %s", dvmClass.value, dvmMethod.methodName, dvmMethod.isStatic ? "is static" : "not static", UnicornPointer.register(emulator, Arm64Const.UC_ARM64_REG_LR)));
                    }

                    return addLocalObject(dvmMethod.toReflectedMethod());
                }
            }
        }) ;

        Pointer _Throw = svcMemory.registerSvc(new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                UnicornPointer object = UnicornPointer.register(emulator, Arm64Const.UC_ARM64_REG_X1);
                DvmObject<?> dvmObject = getObject(object.toUIntPeer());
                log.warn("Throw object=" + object + ", dvmObject=" + dvmObject + ", class=" + dvmObject.getObjectType());
                throwable = dvmObject;
                return 0;
            }
        });

        Pointer _ExceptionOccurred = svcMemory.registerSvc(new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                if (log.isDebugEnabled()) {
                    log.debug("ExceptionOccurred");
                }
                return throwable == null ? JNI_NULL : (throwable.hashCode() & 0xffffffffL);
            }
        });

        Pointer _ExceptionClear = svcMemory.registerSvc(new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                if (log.isDebugEnabled()) {
                    log.debug("ExceptionClear");
                }
                throwable = null;
                return 0;
            }
        });

        Pointer _PushLocalFrame = svcMemory.registerSvc(new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                int capacity = ((Number) emulator.getUnicorn().reg_read(Arm64Const.UC_ARM64_REG_X1)).intValue();
                if (log.isDebugEnabled()) {
                    log.debug("PushLocalFrame capacity=" + capacity);
                }
                return JNI_OK;
            }
        });

        Pointer _PopLocalFrame = svcMemory.registerSvc(new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                UnicornPointer jresult = UnicornPointer.register(emulator, Arm64Const.UC_ARM64_REG_X1);
                if (log.isDebugEnabled()) {
                    log.debug("PopLocalFrame jresult=" + jresult);
                }
                return jresult == null ? 0 : jresult.toUIntPeer();
            }
        });

        Pointer _NewGlobalRef = svcMemory.registerSvc(new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                UnicornPointer object = UnicornPointer.register(emulator, Arm64Const.UC_ARM64_REG_X1);
                if (object == null) {
                    return 0;
                }
                DvmObject<?> dvmObject = getObject(object.toUIntPeer());
                if (log.isDebugEnabled()) {
                    log.debug("NewGlobalRef object=" + object + ", dvmObject=" + dvmObject + ", class=" + dvmObject.getClass());
                }
                addObject(dvmObject, true);
                return object.toUIntPeer();
            }
        });

        Pointer _DeleteGlobalRef = svcMemory.registerSvc(new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                UnicornPointer object = UnicornPointer.register(emulator, Arm64Const.UC_ARM64_REG_X0);
                if (log.isDebugEnabled()) {
                    log.debug("DeleteGlobalRef object=" + object);
                }
                globalObjectMap.remove(object.toUIntPeer());
                return 0;
            }
        });

        Pointer _DeleteLocalRef = svcMemory.registerSvc(new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                UnicornPointer object = UnicornPointer.register(emulator, Arm64Const.UC_ARM64_REG_X0);
                if (log.isDebugEnabled()) {
                    log.debug("DeleteLocalRef object=" + object);
                }
                localObjectMap.remove(object.toUIntPeer());
                return 0;
            }
        });

        Pointer _IsSameObject = svcMemory.registerSvc(new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                UnicornPointer ref1 = UnicornPointer.register(emulator, Arm64Const.UC_ARM64_REG_X1);
                UnicornPointer ref2 = UnicornPointer.register(emulator, Arm64Const.UC_ARM64_REG_X2);
                if (log.isDebugEnabled()) {
                    log.debug("IsSameObject ref1=" + ref1 + ", ref2=" + ref2);
                }
                return ref1 == ref2 || ref1.equals(ref2) ? JNI_TRUE : JNI_FALSE;
            }
        });

        Pointer _NewLocalRef = svcMemory.registerSvc(new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                UnicornPointer object = UnicornPointer.register(emulator, Arm64Const.UC_ARM64_REG_X1);
                DvmObject<?> dvmObject = getObject(object.toUIntPeer());
                if (log.isDebugEnabled()) {
                    log.debug("NewLocalRef object=" + object + ", dvmObject=" + dvmObject + ", class=" + dvmObject.getClass());
                }
                if (verbose) {
                    System.out.println(String.format("JNIEnv->NewLocalRef(0x%x) was called from %s", object.toUIntPeer(), UnicornPointer.register(emulator, Arm64Const.UC_ARM64_REG_LR)));
                }
                return object.toUIntPeer();
            }
        });

        Pointer _EnsureLocalCapacity = svcMemory.registerSvc(new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                int capacity = ((Number) emulator.getUnicorn().reg_read(Arm64Const.UC_ARM64_REG_X1)).intValue();
                if (log.isDebugEnabled()) {
                    log.debug("EnsureLocalCapacity capacity=" + capacity);
                }
                return 0;
            }
        });

        Pointer _NewObject = svcMemory.registerSvc(new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                UnicornPointer clazz = UnicornPointer.register(emulator, Arm64Const.UC_ARM64_REG_X1);
                UnicornPointer jmethodID = UnicornPointer.register(emulator, Arm64Const.UC_ARM64_REG_X2);
                DvmClass dvmClass = classMap.get(clazz.toUIntPeer());
                DvmMethod dvmMethod = dvmClass == null ? null : dvmClass.getMethod(jmethodID.toUIntPeer());
                if (log.isDebugEnabled()) {
                    log.debug("NewObjectV clazz=" + dvmClass + ", jmethodID=" + jmethodID + ", lr=" + UnicornPointer.register(emulator, Arm64Const.UC_ARM64_REG_LR));
                }
                if (dvmMethod == null) {
                    throw new UnicornException();
                } else {
                    if (verbose) {
                        System.out.println(String.format("JNIEnv->NewObject(%s, %s) was called from %s", dvmClass.value, dvmMethod.methodName, UnicornPointer.register(emulator, Arm64Const.UC_ARM64_REG_LR)));
                    }
                    return dvmMethod.newObject(ArmVarArg.create(emulator, DalvikVM64.this));
                }
            }
        });

        Pointer _NewObjectV = svcMemory.registerSvc(new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                UnicornPointer clazz = UnicornPointer.register(emulator, Arm64Const.UC_ARM64_REG_X1);
                UnicornPointer jmethodID = UnicornPointer.register(emulator, Arm64Const.UC_ARM64_REG_X2);
                UnicornPointer va_list = UnicornPointer.register(emulator, Arm64Const.UC_ARM64_REG_X3);
                DvmClass dvmClass = classMap.get(clazz.toUIntPeer());
                DvmMethod dvmMethod = dvmClass == null ? null : dvmClass.getMethod(jmethodID.toUIntPeer());
                if (log.isDebugEnabled()) {
                    log.debug("NewObjectV clazz=" + dvmClass + ", jmethodID=" + jmethodID + ", va_list=" + va_list + ", lr=" + UnicornPointer.register(emulator, Arm64Const.UC_ARM64_REG_LR));
                }
                if (dvmMethod == null) {
                    throw new UnicornException();
                } else {
                    VaList vaList = new VaList64(emulator, DalvikVM64.this, va_list, dvmMethod);
                    DvmObject<?> obj = dvmMethod.newObjectV(vaList);
                    if (verbose) {
                        System.out.println(String.format("JNIEnv->NewObjectV(%s, %s(%s) => %s) was called from %s", dvmClass, dvmMethod.methodName, vaList.formatArgs(), obj, UnicornPointer.register(emulator, Arm64Const.UC_ARM64_REG_LR)));
                    }
                    return addLocalObject(obj);
                }
            }
        });

        Pointer _GetObjectClass = svcMemory.registerSvc(new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                UnicornPointer object = UnicornPointer.register(emulator, Arm64Const.UC_ARM64_REG_X1);
                DvmObject<?> dvmObject = getObject(object.toUIntPeer());
                if (log.isDebugEnabled()) {
                    log.debug("GetObjectClass object=" + object + ", dvmObject=" + dvmObject);
                }
                if (dvmObject == null) {
                    throw new UnicornException();
                } else {
                    DvmClass dvmClass = dvmObject.getObjectType();
                    return dvmClass.hashCode();
                }
            }
        });

        Pointer _IsInstanceOf = svcMemory.registerSvc(new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                UnicornPointer object = UnicornPointer.register(emulator, Arm64Const.UC_ARM64_REG_X1);
                UnicornPointer clazz = UnicornPointer.register(emulator, Arm64Const.UC_ARM64_REG_X2);
                DvmObject<?> dvmObject = getObject(object.toUIntPeer());
                DvmClass dvmClass = classMap.get(clazz.toUIntPeer());
                if (log.isDebugEnabled()) {
                    log.debug("IsInstanceOf object=" + object + ", clazz=" + clazz + ", dvmObject=" + dvmObject + ", dvmClass=" + dvmClass);
                }
                if (dvmObject == null || dvmClass == null) {
                    throw new UnicornException();
                }
                return dvmObject.isInstanceOf(DalvikVM64.this, dvmClass) ? JNI_TRUE : JNI_FALSE;
            }
        });

        Pointer _GetMethodID = svcMemory.registerSvc(new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                UnicornPointer clazz = UnicornPointer.register(emulator, Arm64Const.UC_ARM64_REG_X1);
                Pointer methodName = UnicornPointer.register(emulator, Arm64Const.UC_ARM64_REG_X2);
                Pointer argsPointer = UnicornPointer.register(emulator, Arm64Const.UC_ARM64_REG_X3);
                String name = methodName.getString(0);
                String args = argsPointer.getString(0);
                if (log.isDebugEnabled()) {
                    log.debug("GetMethodID class=" + clazz + ", methodName=" + name + ", args=" + args);
                }
                DvmClass dvmClass = classMap.get(clazz.toUIntPeer());
                if (dvmClass == null) {
                    throw new UnicornException();
                } else {
                    return dvmClass.getMethodID(name, args);
                }
            }
        });

        Pointer _CallObjectMethod = svcMemory.registerSvc(new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                UnicornPointer object = UnicornPointer.register(emulator, Arm64Const.UC_ARM64_REG_X1);
                UnicornPointer jmethodID = UnicornPointer.register(emulator, Arm64Const.UC_ARM64_REG_X2);
                if (log.isDebugEnabled()) {
                    log.debug("CallObjectMethod object=" + object + ", jmethodID=" + jmethodID);
                }
                DvmObject<?> dvmObject = getObject(object.toUIntPeer());
                DvmClass dvmClass = dvmObject == null ? null : dvmObject.getObjectType();
                DvmMethod dvmMethod = dvmClass == null ? null : dvmClass.getMethod(jmethodID.toUIntPeer());
                if (dvmMethod == null) {
                    throw new UnicornException();
                } else {
                    DvmObject<?> ret = dvmMethod.callObjectMethod(dvmObject, ArmVarArg.create(emulator, DalvikVM64.this));
                    if (verbose) {
                        System.out.println(String.format("JNIEnv->CallObjectMethod(%s, %s%s => %s) was called from %s", dvmClass.value, dvmMethod.methodName, dvmMethod.args, ret, UnicornPointer.register(emulator, Arm64Const.UC_ARM64_REG_LR)));
                    }
                    return addObject(ret, false);
                }
            }
        });

        Pointer _CallObjectMethodV = svcMemory.registerSvc(new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                UnicornPointer object = UnicornPointer.register(emulator, Arm64Const.UC_ARM64_REG_X1);
                UnicornPointer jmethodID = UnicornPointer.register(emulator, Arm64Const.UC_ARM64_REG_X2);
                UnicornPointer va_list = UnicornPointer.register(emulator, Arm64Const.UC_ARM64_REG_X3);
                if (log.isDebugEnabled()) {
                    log.debug("CallObjectMethodV object=" + object + ", jmethodID=" + jmethodID + ", va_list=" + va_list + ", lr=" + UnicornPointer.register(emulator, Arm64Const.UC_ARM64_REG_LR));
                }
                DvmObject<?> dvmObject = getObject(object.toUIntPeer());
                DvmClass dvmClass = dvmObject == null ? null : dvmObject.getObjectType();
                DvmMethod dvmMethod = dvmClass == null ? null : dvmClass.getMethod(jmethodID.toUIntPeer());
                if (dvmMethod == null) {
                    throw new UnicornException("dvmObject=" + dvmObject + ", dvmClass=" + dvmClass + ", jmethodID=" + jmethodID);
                } else {
                    VaList vaList = new VaList64(emulator, DalvikVM64.this, va_list, dvmMethod);
                    DvmObject<?> obj = dvmMethod.callObjectMethodV(dvmObject, vaList);
                    if (verbose) {
                        System.out.println(String.format("JNIEnv->CallObjectMethodV(%s, %s(%s) => %s) was called from %s", dvmObject, dvmMethod.methodName, vaList.formatArgs(), obj, UnicornPointer.register(emulator, Arm64Const.UC_ARM64_REG_LR)));
                    }
                    return addObject(obj, false);
                }
            }
        });

        Pointer _CallBooleanMethod = svcMemory.registerSvc(new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                UnicornPointer object = UnicornPointer.register(emulator, Arm64Const.UC_ARM64_REG_X1);
                UnicornPointer jmethodID = UnicornPointer.register(emulator, Arm64Const.UC_ARM64_REG_X2);
                if (log.isDebugEnabled()) {
                    log.debug("CallBooleanMethod object=" + object + ", jmethodID=" + jmethodID);
                }
                DvmObject<?> dvmObject = getObject(object.toUIntPeer());
                DvmClass dvmClass = dvmObject == null ? null : dvmObject.getObjectType();
                DvmMethod dvmMethod = dvmClass == null ? null : dvmClass.getMethod(jmethodID.toUIntPeer());
                if (dvmMethod == null) {
                    throw new UnicornException();
                } else {
                    int ret = dvmMethod.callBooleanMethod(dvmObject, ArmVarArg.create(emulator, DalvikVM64.this));
                    if (verbose) {
                        System.out.println(String.format("JNIEnv->CallBooleanMethod(%s, %s%s => %s) was called from %s", dvmClass.value, dvmMethod.methodName, dvmMethod.args, ret == VM.JNI_TRUE, UnicornPointer.register(emulator, Arm64Const.UC_ARM64_REG_LR)));
                    }
                    return ret;
                }
            }
        });

        Pointer _CallBooleanMethodV = svcMemory.registerSvc(new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                UnicornPointer object = UnicornPointer.register(emulator, Arm64Const.UC_ARM64_REG_X1);
                UnicornPointer jmethodID = UnicornPointer.register(emulator, Arm64Const.UC_ARM64_REG_X2);
                UnicornPointer va_list = UnicornPointer.register(emulator, Arm64Const.UC_ARM64_REG_X3);
                if (log.isDebugEnabled()) {
                    log.debug("CallBooleanMethodV object=" + object + ", jmethodID=" + jmethodID + ", va_list=" + va_list);
                }
                DvmObject<?> dvmObject = getObject(object.toUIntPeer());
                DvmClass dvmClass = dvmObject == null ? null : dvmObject.getObjectType();
                DvmMethod dvmMethod = dvmClass == null ? null : dvmClass.getMethod(jmethodID.toUIntPeer());
                if (dvmMethod == null) {
                    throw new UnicornException();
                } else {
                    VaList vaList = new VaList64(emulator, DalvikVM64.this, va_list, dvmMethod);
                    int ret = dvmMethod.callBooleanMethodV(dvmObject, vaList);
                    if (verbose) {
                        System.out.println(String.format("JNIEnv->CallBooleanMethodV(%s, %s(%s) => %s) was called from %s", dvmObject, dvmMethod.methodName, vaList.formatArgs(), ret == JNI_TRUE, UnicornPointer.register(emulator, Arm64Const.UC_ARM64_REG_LR)));
                    }
                    return ret;
                }
            }
        });

        Pointer _CallIntMethod = svcMemory.registerSvc(new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                UnicornPointer object = UnicornPointer.register(emulator, Arm64Const.UC_ARM64_REG_X1);
                UnicornPointer jmethodID = UnicornPointer.register(emulator, Arm64Const.UC_ARM64_REG_X2);
                if (log.isDebugEnabled()) {
                    log.debug("CallIntMethod object=" + object + ", jmethodID=" + jmethodID);
                }
                DvmObject<?> dvmObject = getObject(object.toUIntPeer());
                DvmClass dvmClass = dvmObject == null ? null : dvmObject.getObjectType();
                DvmMethod dvmMethod = dvmClass == null ? null : dvmClass.getMethod(jmethodID.toUIntPeer());
                if (dvmMethod == null) {
                    throw new UnicornException();
                } else {
                    int ret = dvmMethod.callIntMethod(dvmObject, ArmVarArg.create(emulator, DalvikVM64.this));
                    if (verbose) {
                        System.out.println(String.format("JNIEnv->CallIntMethod(%s, %s%s => 0x%x) was called from %s", dvmClass.value, dvmMethod.methodName, dvmMethod.args, ret, UnicornPointer.register(emulator, Arm64Const.UC_ARM64_REG_LR)));
                    }
                    return ret;
                }
            }
        });

        Pointer _CallIntMethodV = svcMemory.registerSvc(new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                UnicornPointer object = UnicornPointer.register(emulator, Arm64Const.UC_ARM64_REG_X1);
                UnicornPointer jmethodID = UnicornPointer.register(emulator, Arm64Const.UC_ARM64_REG_X2);
                UnicornPointer va_list = UnicornPointer.register(emulator, Arm64Const.UC_ARM64_REG_X3);
                if (log.isDebugEnabled()) {
                    log.debug("CallIntMethodV object=" + object + ", jmethodID=" + jmethodID + ", va_list=" + va_list);
                }
                DvmObject<?> dvmObject = getObject(object.toUIntPeer());
                DvmClass dvmClass = dvmObject == null ? null : dvmObject.getObjectType();
                DvmMethod dvmMethod = dvmClass == null ? null : dvmClass.getMethod(jmethodID.toUIntPeer());
                if (dvmMethod == null) {
                    throw new UnicornException();
                } else {
                    VaList vaList = new VaList64(emulator, DalvikVM64.this, va_list, dvmMethod);
                    int ret = dvmMethod.callIntMethodV(dvmObject, vaList);
                    if (verbose) {
                        System.out.println(String.format("JNIEnv->CallIntMethodV(%s, %s(%s) => 0x%x) was called from %s", dvmObject, dvmMethod.methodName, vaList.formatArgs(), ret, UnicornPointer.register(emulator, Arm64Const.UC_ARM64_REG_LR)));
                    }
                    return ret;
                }
            }
        });

        Pointer _CallLongMethodV = svcMemory.registerSvc(new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                EditableArm64RegisterContext context = emulator.getContext();
                UnicornPointer object = context.getPointerArg(1);
                UnicornPointer jmethodID = context.getPointerArg(2);
                UnicornPointer va_list = context.getPointerArg(3);
                if (log.isDebugEnabled()) {
                    log.debug("CallLongMethodV object=" + object + ", jmethodID=" + jmethodID + ", va_list=" + va_list);
                }
                DvmObject<?> dvmObject = getObject(object.toUIntPeer());
                DvmClass dvmClass = dvmObject == null ? null : dvmObject.getObjectType();
                DvmMethod dvmMethod = dvmClass == null ? null : dvmClass.getMethod(jmethodID.toUIntPeer());
                if (dvmMethod == null) {
                    throw new UnicornException();
                } else {
                    VaList vaList = new VaList64(emulator, DalvikVM64.this, va_list, dvmMethod);
                    long ret = dvmMethod.callLongMethodV(dvmObject, vaList);
                    if (verbose) {
                        System.out.println(String.format("JNIEnv->CallLongMethodV(%s, %s(%s) => 0x%xL) was called from %s", dvmObject, dvmMethod.methodName, vaList.formatArgs(), ret, UnicornPointer.register(emulator, Arm64Const.UC_ARM64_REG_LR)));
                    }
                    return ret;
                }
            }
        });

        Pointer _CallFloatMethodV = svcMemory.registerSvc(new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                RegisterContext context = emulator.getContext();
                UnicornPointer object = context.getPointerArg(1);
                UnicornPointer jmethodID = context.getPointerArg(2);
                UnicornPointer va_list = context.getPointerArg(3);
                if (log.isDebugEnabled()) {
                    log.debug("CallFloatMethodV object=" + object + ", jmethodID=" + jmethodID + ", va_list=" + va_list);
                }
                DvmObject<?> dvmObject = getObject(object.toUIntPeer());
                DvmClass dvmClass = dvmObject == null ? null : dvmObject.getObjectType();
                DvmMethod dvmMethod = dvmClass == null ? null : dvmClass.getMethod(jmethodID.toUIntPeer());
                if (dvmMethod == null) {
                    throw new UnicornException();
                } else {
                    VaList vaList = new VaList64(emulator, DalvikVM64.this, va_list, dvmMethod);
                    float ret = dvmMethod.callFloatMethodV(dvmObject, vaList);
                    if (verbose) {
                        System.out.println(String.format("JNIEnv->CallFloatMethodV(%s, %s(%s) => %s) was called from %s", dvmObject, dvmMethod.methodName, vaList.formatArgs(), ret, UnicornPointer.register(emulator, Arm64Const.UC_ARM64_REG_LR)));
                    }
                    ByteBuffer buffer = ByteBuffer.allocate(4);
                    buffer.order(ByteOrder.LITTLE_ENDIAN);
                    buffer.putFloat(ret);
                    buffer.flip();
                    return (buffer.getInt() & 0xffffffffL);
                }
            }
        });

        Pointer _CallVoidMethod = svcMemory.registerSvc(new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                UnicornPointer object = UnicornPointer.register(emulator, Arm64Const.UC_ARM64_REG_X1);
                UnicornPointer jmethodID = UnicornPointer.register(emulator, Arm64Const.UC_ARM64_REG_X2);
                if (log.isDebugEnabled()) {
                    log.debug("CallVoidMethod object=" + object + ", jmethodID=" + jmethodID);
                }
                DvmObject<?> dvmObject = getObject(object.toUIntPeer());
                DvmClass dvmClass = dvmObject == null ? null : dvmObject.getObjectType();
                DvmMethod dvmMethod = dvmClass == null ? null : dvmClass.getMethod(jmethodID.toUIntPeer());
                if (dvmMethod == null) {
                    throw new UnicornException();
                } else {
                    if (verbose) {
                        System.out.println(String.format("JNIEnv->CallVoidMethod(%s, %s%s) was called from %s", dvmClass.value, dvmMethod.methodName, dvmMethod.args, UnicornPointer.register(emulator, Arm64Const.UC_ARM64_REG_LR)));
                    }
                    dvmMethod.callVoidMethod(dvmObject, ArmVarArg.create(emulator, DalvikVM64.this));
                    return 0;
                }
            }
        });

        Pointer _CallVoidMethodV = svcMemory.registerSvc(new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                UnicornPointer object = UnicornPointer.register(emulator, Arm64Const.UC_ARM64_REG_X1);
                UnicornPointer jmethodID = UnicornPointer.register(emulator, Arm64Const.UC_ARM64_REG_X2);
                UnicornPointer va_list = UnicornPointer.register(emulator, Arm64Const.UC_ARM64_REG_X3);
                if (log.isDebugEnabled()) {
                    log.debug("CallVoidMethodV object=" + object + ", jmethodID=" + jmethodID + ", va_list=" + va_list);
                }
                DvmObject<?> dvmObject = getObject(object.toUIntPeer());
                DvmClass dvmClass = dvmObject == null ? null : dvmObject.getObjectType();
                DvmMethod dvmMethod = dvmClass == null ? null : dvmClass.getMethod(jmethodID.toUIntPeer());
                if (dvmMethod == null) {
                    throw new UnicornException();
                } else {
                    VaList vaList = new VaList64(emulator, DalvikVM64.this, va_list, dvmMethod);
                    dvmMethod.callVoidMethodV(dvmObject, vaList);
                    if (verbose) {
                        System.out.println(String.format("JNIEnv->CallVoidMethodV(%s, %s(%s)) was called from %s", dvmClass.value, dvmMethod.methodName, vaList.formatArgs(), UnicornPointer.register(emulator, Arm64Const.UC_ARM64_REG_LR)));
                    }
                    return 0;
                }
            }
        });

        Pointer _GetFieldID = svcMemory.registerSvc(new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                UnicornPointer clazz = UnicornPointer.register(emulator, Arm64Const.UC_ARM64_REG_X1);
                Pointer fieldName = UnicornPointer.register(emulator, Arm64Const.UC_ARM64_REG_X2);
                Pointer argsPointer = UnicornPointer.register(emulator, Arm64Const.UC_ARM64_REG_X3);
                String name = fieldName.getString(0);
                String args = argsPointer.getString(0);
                if (log.isDebugEnabled()) {
                    log.debug("GetFieldID class=" + clazz + ", fieldName=" + name + ", args=" + args);
                }
                DvmClass dvmClass = classMap.get(clazz.toUIntPeer());
                if (dvmClass == null) {
                    throw new UnicornException();
                } else {
                    return dvmClass.getFieldID(name, args);
                }
            }
        });

        Pointer _GetObjectField = svcMemory.registerSvc(new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                UnicornPointer object = UnicornPointer.register(emulator, Arm64Const.UC_ARM64_REG_X1);
                UnicornPointer jfieldID = UnicornPointer.register(emulator, Arm64Const.UC_ARM64_REG_X2);
                if (log.isDebugEnabled()) {
                    log.debug("GetObjectField object=" + object + ", jfieldID=" + jfieldID);
                }
                DvmObject<?> dvmObject = getObject(object.toUIntPeer());
                DvmClass dvmClass = dvmObject == null ? null : dvmObject.getObjectType();
                DvmField dvmField = dvmClass == null ? null : dvmClass.getField(jfieldID.toUIntPeer());
                if (dvmField == null) {
                    throw new UnicornException();
                } else {
                    DvmObject<?> obj = dvmField.getObjectField(dvmObject);
                    if (verbose) {
                        System.out.println(String.format("JNIEnv->GetObjectField(%s, %s %s => %s) was called from %s", dvmObject, dvmField.fieldName, dvmField.fieldType, obj, UnicornPointer.register(emulator, Arm64Const.UC_ARM64_REG_LR)));
                    }
                    return addLocalObject(obj);
                }
            }
        });

        Pointer _GetBooleanField = svcMemory.registerSvc(new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                UnicornPointer object = UnicornPointer.register(emulator, Arm64Const.UC_ARM64_REG_X1);
                UnicornPointer jfieldID = UnicornPointer.register(emulator, Arm64Const.UC_ARM64_REG_X2);
                if (log.isDebugEnabled()) {
                    log.debug("GetBooleanField object=" + object + ", jfieldID=" + jfieldID);
                }
                DvmObject<?> dvmObject = getObject(object.toUIntPeer());
                DvmClass dvmClass = dvmObject == null ? null : dvmObject.getObjectType();
                DvmField dvmField = dvmClass == null ? null : dvmClass.getField(jfieldID.toUIntPeer());
                if (dvmField == null) {
                    throw new UnicornException();
                } else {
                    int ret = dvmField.getBooleanField(dvmObject);
                    if (verbose) {
                        System.out.println(String.format("JNIEnv->GetBooleanField(%s, %s => %s) was called from %s", dvmObject, dvmField.fieldName, ret == JNI_TRUE, UnicornPointer.register(emulator, Arm64Const.UC_ARM64_REG_LR)));
                    }
                    return ret;
                }
            }
        });

        Pointer _GetIntField = svcMemory.registerSvc(new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                UnicornPointer object = UnicornPointer.register(emulator, Arm64Const.UC_ARM64_REG_X1);
                UnicornPointer jfieldID = UnicornPointer.register(emulator, Arm64Const.UC_ARM64_REG_X2);
                if (log.isDebugEnabled()) {
                    log.debug("GetIntField object=" + object + ", jfieldID=" + jfieldID);
                }
                DvmObject<?> dvmObject = getObject(object.toUIntPeer());
                DvmClass dvmClass = dvmObject == null ? null : dvmObject.getObjectType();
                DvmField dvmField = dvmClass == null ? null : dvmClass.getField(jfieldID.toUIntPeer());
                if (dvmField == null) {
                    throw new UnicornException();
                } else {
                    int ret = dvmField.getIntField(dvmObject);
                    if (verbose) {
                        System.out.println(String.format("JNIEnv->GetIntField(%s, %s => 0x%x) was called from %s", dvmObject, dvmField.fieldName, ret, UnicornPointer.register(emulator, Arm64Const.UC_ARM64_REG_LR)));
                    }
                    return ret;
                }
            }
        });

        Pointer _GetLongField = svcMemory.registerSvc(new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                UnicornPointer object = UnicornPointer.register(emulator, Arm64Const.UC_ARM64_REG_X1);
                UnicornPointer jfieldID = UnicornPointer.register(emulator, Arm64Const.UC_ARM64_REG_X2);
                if (log.isDebugEnabled()) {
                    log.debug("GetLongField object=" + object + ", jfieldID=" + jfieldID);
                }
                DvmObject<?> dvmObject = getObject(object.toUIntPeer());
                DvmClass dvmClass = dvmObject == null ? null : dvmObject.getObjectType();
                DvmField dvmField = dvmClass == null ? null : dvmClass.getField(jfieldID.toUIntPeer());
                if (dvmField == null) {
                    throw new UnicornException();
                } else {
                    long ret = dvmField.getLongField(dvmObject);
                    if (verbose) {
                        System.out.println(String.format("JNIEnv->GetLongField(%s, %s => 0x%x) was called from %s", dvmObject, dvmField.fieldName, ret, UnicornPointer.register(emulator, Arm64Const.UC_ARM64_REG_LR)));
                    }
                    return ret;
                }
            }
        });

        Pointer _SetObjectField = svcMemory.registerSvc(new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                UnicornPointer object = UnicornPointer.register(emulator, Arm64Const.UC_ARM64_REG_X1);
                UnicornPointer jfieldID = UnicornPointer.register(emulator, Arm64Const.UC_ARM64_REG_X2);
                UnicornPointer value = UnicornPointer.register(emulator, Arm64Const.UC_ARM64_REG_X3);
                if (log.isDebugEnabled()) {
                    log.debug("SetObjectField object=" + object + ", jfieldID=" + jfieldID + ", value=" + value);
                }
                DvmObject<?> dvmObject = getObject(object.toUIntPeer());
                DvmClass dvmClass = dvmObject == null ? null : dvmObject.getObjectType();
                DvmField dvmField = dvmClass == null ? null : dvmClass.getField(jfieldID.toUIntPeer());
                if (dvmField == null) {
                    throw new UnicornException();
                } else {
                    DvmObject<?> obj = getObject(value.toUIntPeer());
                    dvmField.setObjectField(dvmObject, obj);
                    if (verbose) {
                        System.out.println(String.format("JNIEnv->SetObjectField(%s, %s %s => %s) was called from %s", dvmObject, dvmField.fieldName, dvmField.fieldType, obj, UnicornPointer.register(emulator, Arm64Const.UC_ARM64_REG_LR)));
                    }
                }
                return 0;
            }
        });

        Pointer _SetBooleanField = svcMemory.registerSvc(new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                UnicornPointer object = UnicornPointer.register(emulator, Arm64Const.UC_ARM64_REG_X1);
                UnicornPointer jfieldID = UnicornPointer.register(emulator, Arm64Const.UC_ARM64_REG_X2);
                int value = ((Number) emulator.getUnicorn().reg_read(Arm64Const.UC_ARM64_REG_X3)).intValue();
                if (log.isDebugEnabled()) {
                    log.debug("SetBooleanField object=" + object + ", jfieldID=" + jfieldID + ", value=" + value);
                }
                DvmObject<?> dvmObject = getObject(object.toUIntPeer());
                DvmClass dvmClass = dvmObject == null ? null : dvmObject.getObjectType();
                DvmField dvmField = dvmClass == null ? null : dvmClass.getField(jfieldID.toUIntPeer());
                if (dvmField == null) {
                    throw new UnicornException();
                } else {
                    boolean flag = value == JNI_TRUE;
                    dvmField.setBooleanField(dvmObject, flag);
                    if (verbose) {
                        System.out.println(String.format("JNIEnv->SetBooleanField(%s, %s => %s) was called from %s", dvmObject, dvmField.fieldName, flag, UnicornPointer.register(emulator, Arm64Const.UC_ARM64_REG_LR)));
                    }
                }
                return 0;
            }
        });

        Pointer _SetIntField = svcMemory.registerSvc(new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                UnicornPointer object = UnicornPointer.register(emulator, Arm64Const.UC_ARM64_REG_X1);
                UnicornPointer jfieldID = UnicornPointer.register(emulator, Arm64Const.UC_ARM64_REG_X2);
                int value = ((Number) emulator.getUnicorn().reg_read(Arm64Const.UC_ARM64_REG_X3)).intValue();
                if (log.isDebugEnabled()) {
                    log.debug("SetIntField object=" + object + ", jfieldID=" + jfieldID + ", value=" + value);
                }
                DvmObject<?> dvmObject = getObject(object.toUIntPeer());
                DvmClass dvmClass = dvmObject == null ? null : dvmObject.getObjectType();
                DvmField dvmField = dvmClass == null ? null : dvmClass.getField(jfieldID.toUIntPeer());
                if (dvmField == null) {
                    throw new UnicornException();
                } else {
                    dvmField.setIntField(dvmObject, value);
                    if (verbose) {
                        System.out.println(String.format("JNIEnv->SetIntField(%s, %s => 0x%x) was called from %s", dvmObject, dvmField.fieldName, value, UnicornPointer.register(emulator, Arm64Const.UC_ARM64_REG_LR)));
                    }
                }
                return 0;
            }
        });

        Pointer _SetLongField = svcMemory.registerSvc(new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                UnicornPointer object = UnicornPointer.register(emulator, Arm64Const.UC_ARM64_REG_X1);
                UnicornPointer jfieldID = UnicornPointer.register(emulator, Arm64Const.UC_ARM64_REG_X2);
                long value = ((Number) emulator.getUnicorn().reg_read(Arm64Const.UC_ARM64_REG_X3)).longValue();
                if (log.isDebugEnabled()) {
                    log.debug("SetLongField object=" + object + ", jfieldID=" + jfieldID + ", value=" + value);
                }
                DvmObject<?> dvmObject = getObject(object.toUIntPeer());
                DvmClass dvmClass = dvmObject == null ? null : dvmObject.getObjectType();
                DvmField dvmField = dvmClass == null ? null : dvmClass.getField(jfieldID.toUIntPeer());
                if (dvmField == null) {
                    throw new UnicornException();
                } else {
                    dvmField.setLongField(dvmObject, value);
                    if (verbose) {
                        System.out.println(String.format("JNIEnv->SetLongField(%s, %s => 0x%x) was called from %s", dvmObject, dvmField.fieldName, value, UnicornPointer.register(emulator, Arm64Const.UC_ARM64_REG_LR)));
                    }
                }
                return 0;
            }
        });

        Pointer _SetDoubleField = svcMemory.registerSvc(new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                RegisterContext context = emulator.getContext();
                UnicornPointer object = context.getPointerArg(1);
                UnicornPointer jfieldID = context.getPointerArg(2);
                ByteBuffer buffer = ByteBuffer.allocate(8);
                buffer.order(ByteOrder.LITTLE_ENDIAN);
                buffer.putLong(context.getLongArg(3));
                buffer.flip();
                double value = buffer.getDouble();
                if (log.isDebugEnabled()) {
                    log.debug("SetDoubleField object=" + object + ", jfieldID=" + jfieldID + ", value=" + value);
                }
                DvmObject<?> dvmObject = getObject(object.toUIntPeer());
                DvmClass dvmClass = dvmObject == null ? null : dvmObject.getObjectType();
                DvmField dvmField = dvmClass == null ? null : dvmClass.getField(jfieldID.toUIntPeer());
                if (dvmField == null) {
                    throw new UnicornException();
                } else {
                    dvmField.setDoubleField(dvmObject, value);
                    if (verbose) {
                        System.out.println(String.format("JNIEnv->SetDoubleField(%s, %s => %s) was called from %s", dvmObject, dvmField.fieldName, value, UnicornPointer.register(emulator, Arm64Const.UC_ARM64_REG_LR)));
                    }
                }
                return 0;
            }
        });

        Pointer _GetStaticMethodID = svcMemory.registerSvc(new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                UnicornPointer clazz = UnicornPointer.register(emulator, Arm64Const.UC_ARM64_REG_X1);
                Pointer methodName = UnicornPointer.register(emulator, Arm64Const.UC_ARM64_REG_X2);
                Pointer argsPointer = UnicornPointer.register(emulator, Arm64Const.UC_ARM64_REG_X3);
                String name = methodName.getString(0);
                String args = argsPointer.getString(0);
                if (log.isDebugEnabled()) {
                    log.debug("GetStaticMethodID class=" + clazz + ", methodName=" + name + ", args=" + args);
                }
                DvmClass dvmClass = classMap.get(clazz.toUIntPeer());
                if (dvmClass == null) {
                    throw new UnicornException();
                } else {
                    return dvmClass.getStaticMethodID(name, args);
                }
            }
        });

        Pointer _CallStaticObjectMethod = svcMemory.registerSvc(new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                UnicornPointer clazz = UnicornPointer.register(emulator, Arm64Const.UC_ARM64_REG_X1);
                UnicornPointer jmethodID = UnicornPointer.register(emulator, Arm64Const.UC_ARM64_REG_X2);
                if (log.isDebugEnabled()) {
                    log.debug("CallStaticObjectMethod clazz=" + clazz + ", jmethodID=" + jmethodID);
                }
                DvmClass dvmClass = classMap.get(clazz.toUIntPeer());
                DvmMethod dvmMethod = dvmClass == null ? null : dvmClass.getStaticMethod(jmethodID.toUIntPeer());
                if (dvmMethod == null) {
                    throw new UnicornException();
                } else {
                    if (verbose) {
                        System.out.println(String.format("JNIEnv->CallStaticObjectMethod(%s, %s%s) was called from %s", dvmClass, dvmMethod.methodName, dvmMethod.args, UnicornPointer.register(emulator, Arm64Const.UC_ARM64_REG_LR)));
                    }
                    return addObject(dvmMethod.callStaticObjectMethod(ArmVarArg.create(emulator, DalvikVM64.this)), false);
                }
            }
        });

        Pointer _CallStaticObjectMethodV = svcMemory.registerSvc(new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                UnicornPointer clazz = UnicornPointer.register(emulator, Arm64Const.UC_ARM64_REG_X1);
                UnicornPointer jmethodID = UnicornPointer.register(emulator, Arm64Const.UC_ARM64_REG_X2);
                UnicornPointer va_list = UnicornPointer.register(emulator, Arm64Const.UC_ARM64_REG_X3);
                if (log.isDebugEnabled()) {
                    log.debug("CallStaticObjectMethodV clazz=" + clazz + ", jmethodID=" + jmethodID + ", va_list=" + va_list);
                }
                DvmClass dvmClass = classMap.get(clazz.toUIntPeer());
                DvmMethod dvmMethod = dvmClass == null ? null : dvmClass.getStaticMethod(jmethodID.toUIntPeer());
                if (dvmMethod == null) {
                    throw new UnicornException();
                } else {
                    VaList vaList = new VaList64(emulator, DalvikVM64.this, va_list, dvmMethod);
                    DvmObject<?> obj = dvmMethod.callStaticObjectMethodV(vaList);
                    if (verbose) {
                        System.out.println(String.format("JNIEnv->CallStaticObjectMethodV(%s, %s(%s) => %s) was called from %s", dvmClass, dvmMethod.methodName, vaList.formatArgs(), obj, UnicornPointer.register(emulator, Arm64Const.UC_ARM64_REG_LR)));
                    }
                    return addObject(obj, false);
                }
            }
        });

        Pointer _CallStaticBooleanMethod = svcMemory.registerSvc(new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                UnicornPointer clazz = UnicornPointer.register(emulator, Arm64Const.UC_ARM64_REG_X1);
                UnicornPointer jmethodID = UnicornPointer.register(emulator, Arm64Const.UC_ARM64_REG_X2);
                if (log.isDebugEnabled()) {
                    log.debug("CallStaticBooleanMethod clazz=" + clazz + ", jmethodID=" + jmethodID);
                }
                DvmClass dvmClass = classMap.get(clazz.toUIntPeer());
                DvmMethod dvmMethod = dvmClass == null ? null : dvmClass.getStaticMethod(jmethodID.toUIntPeer());
                if (dvmMethod == null) {
                    throw new UnicornException();
                } else {
                    if (verbose) {
                        System.out.println(String.format("JNIEnv->CallStaticBooleanMethod(%s, %s%s) was called from %s", dvmClass, dvmMethod.methodName, dvmMethod.args, UnicornPointer.register(emulator, Arm64Const.UC_ARM64_REG_LR)));
                    }
                    return dvmMethod.CallStaticBooleanMethod(ArmVarArg.create(emulator, DalvikVM64.this));
                }
            }
        });

        Pointer _CallStaticBooleanMethodV = svcMemory.registerSvc(new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                UnicornPointer clazz = UnicornPointer.register(emulator, Arm64Const.UC_ARM64_REG_X1);
                UnicornPointer jmethodID = UnicornPointer.register(emulator, Arm64Const.UC_ARM64_REG_X2);
                UnicornPointer va_list = UnicornPointer.register(emulator, Arm64Const.UC_ARM64_REG_X3);
                if (log.isDebugEnabled()) {
                    log.debug("CallStaticBooleanMethodV clazz=" + clazz + ", jmethodID=" + jmethodID + ", va_list=" + va_list);
                }
                DvmClass dvmClass = classMap.get(clazz.toUIntPeer());
                DvmMethod dvmMethod = dvmClass == null ? null : dvmClass.getStaticMethod(jmethodID.toUIntPeer());
                if (dvmMethod == null) {
                    throw new UnicornException();
                } else {
                    VaList vaList = new VaList64(emulator, DalvikVM64.this, va_list, dvmMethod);
                    int ret = dvmMethod.callStaticBooleanMethodV(vaList);
                    if (verbose) {
                        System.out.println(String.format("JNIEnv->CallStaticBooleanMethodV(%s, %s(%s) => %s) was called from %s", dvmClass, dvmMethod.methodName, vaList.formatArgs(), ret == JNI_TRUE, UnicornPointer.register(emulator, Arm64Const.UC_ARM64_REG_LR)));
                    }
                    return ret;
                }
            }
        });

        Pointer _CallStaticIntMethod = svcMemory.registerSvc(new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                UnicornPointer clazz = UnicornPointer.register(emulator, Arm64Const.UC_ARM64_REG_X1);
                UnicornPointer jmethodID = UnicornPointer.register(emulator, Arm64Const.UC_ARM64_REG_X2);
                if (log.isDebugEnabled()) {
                    log.debug("CallStaticIntMethodV clazz=" + clazz + ", jmethodID=" + jmethodID);
                }
                DvmClass dvmClass = classMap.get(clazz.toUIntPeer());
                DvmMethod dvmMethod = dvmClass == null ? null : dvmClass.getStaticMethod(jmethodID.toUIntPeer());
                if (dvmMethod == null) {
                    throw new UnicornException();
                } else {
                    if (verbose) {
                        System.out.println(String.format("JNIEnv->CallStaticIntMethod(%s, %s%s) was called from %s", dvmClass, dvmMethod.methodName, dvmMethod.args, UnicornPointer.register(emulator, Arm64Const.UC_ARM64_REG_LR)));
                    }
                    return dvmMethod.callStaticIntMethod(ArmVarArg.create(emulator, DalvikVM64.this));
                }
            }
        });

        Pointer _CallStaticIntMethodV = svcMemory.registerSvc(new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                UnicornPointer clazz = UnicornPointer.register(emulator, Arm64Const.UC_ARM64_REG_X1);
                UnicornPointer jmethodID = UnicornPointer.register(emulator, Arm64Const.UC_ARM64_REG_X2);
                UnicornPointer va_list = UnicornPointer.register(emulator, Arm64Const.UC_ARM64_REG_X3);
                if (log.isDebugEnabled()) {
                    log.debug("CallStaticIntMethodV clazz=" + clazz + ", jmethodID=" + jmethodID + ", va_list=" + va_list);
                }
                DvmClass dvmClass = classMap.get(clazz.toUIntPeer());
                DvmMethod dvmMethod = dvmClass == null ? null : dvmClass.getStaticMethod(jmethodID.toUIntPeer());
                if (dvmMethod == null) {
                    throw new UnicornException();
                } else {
                    VaList vaList = new VaList64(emulator, DalvikVM64.this, va_list, dvmMethod);
                    int ret = dvmMethod.callStaticIntMethodV(vaList);
                    if (verbose) {
                        System.out.println(String.format("JNIEnv->CallStaticIntMethodV(%s, %s(%s) => 0x%x) was called from %s", dvmClass, dvmMethod.methodName, vaList.formatArgs(), ret, UnicornPointer.register(emulator, Arm64Const.UC_ARM64_REG_LR)));
                    }
                    return ret;
                }
            }
        });

        Pointer _CallStaticLongMethodV = svcMemory.registerSvc(new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                UnicornPointer clazz = UnicornPointer.register(emulator, Arm64Const.UC_ARM64_REG_X1);
                UnicornPointer jmethodID = UnicornPointer.register(emulator, Arm64Const.UC_ARM64_REG_X2);
                UnicornPointer va_list = UnicornPointer.register(emulator, Arm64Const.UC_ARM64_REG_X3);
                if (log.isDebugEnabled()) {
                    log.debug("CallStaticLongMethodV clazz=" + clazz + ", jmethodID=" + jmethodID + ", va_list=" + va_list + ", lr=" + UnicornPointer.register(emulator, Arm64Const.UC_ARM64_REG_LR));
                }
                DvmClass dvmClass = classMap.get(clazz.toUIntPeer());
                DvmMethod dvmMethod = dvmClass == null ? null : dvmClass.getStaticMethod(jmethodID.toUIntPeer());
                if (dvmMethod == null) {
                    throw new UnicornException();
                } else {
                    VaList vaList = new VaList64(emulator, DalvikVM64.this, va_list, dvmMethod);
                    long ret = dvmMethod.callStaticLongMethodV(vaList);
                    if (verbose) {
                        System.out.println(String.format("JNIEnv->CallStaticLongMethodV(%s, %s(%s) => 0x%x) was called from %s", dvmClass, dvmMethod.methodName, vaList.formatArgs(), ret, UnicornPointer.register(emulator, Arm64Const.UC_ARM64_REG_LR)));
                    }
                    emulator.getUnicorn().reg_write(Arm64Const.UC_ARM64_REG_X1, (int) (ret >> 32));
                    return (ret & 0xffffffffL);
                }
            }
        });

        Pointer _CallStaticVoidMethod = svcMemory.registerSvc(new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                UnicornPointer clazz = UnicornPointer.register(emulator, Arm64Const.UC_ARM64_REG_X1);
                UnicornPointer jmethodID = UnicornPointer.register(emulator, Arm64Const.UC_ARM64_REG_X2);
                if (log.isDebugEnabled()) {
                    log.debug("CallStaticVoidMethod clazz=" + clazz + ", jmethodID=" + jmethodID);
                }
                DvmClass dvmClass = classMap.get(clazz.toUIntPeer());
                DvmMethod dvmMethod = dvmClass == null ? null : dvmClass.getStaticMethod(jmethodID.toUIntPeer());
                if (dvmMethod == null) {
                    throw new UnicornException();
                } else {
                    if (verbose) {
                        System.out.println(String.format("JNIEnv->CallStaticVoidMethod(%s, %s%s) was called from %s", dvmClass, dvmMethod.methodName, dvmMethod.args, UnicornPointer.register(emulator, Arm64Const.UC_ARM64_REG_LR)));
                    }
                    dvmMethod.callStaticVoidMethod(ArmVarArg.create(emulator, DalvikVM64.this));
                    return 0;
                }
            }
        });

        Pointer _CallStaticVoidMethodV = svcMemory.registerSvc(new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                UnicornPointer clazz = UnicornPointer.register(emulator, Arm64Const.UC_ARM64_REG_X1);
                UnicornPointer jmethodID = UnicornPointer.register(emulator, Arm64Const.UC_ARM64_REG_X2);
                UnicornPointer va_list = UnicornPointer.register(emulator, Arm64Const.UC_ARM64_REG_X3);
                if (log.isDebugEnabled()) {
                    log.debug("CallStaticVoidMethodV clazz=" + clazz + ", jmethodID=" + jmethodID + ", va_list=" + va_list);
                }
                DvmClass dvmClass = classMap.get(clazz.toUIntPeer());
                DvmMethod dvmMethod = dvmClass == null ? null : dvmClass.getStaticMethod(jmethodID.toUIntPeer());
                if (dvmMethod == null) {
                    throw new UnicornException();
                } else {
                    VaList vaList = new VaList64(emulator, DalvikVM64.this, va_list, dvmMethod);
                    dvmMethod.callStaticVoidMethodV(vaList);
                    if (verbose) {
                        System.out.println(String.format("JNIEnv->CallStaticVoidMethodV(%s, %s(%s)) was called from %s", dvmClass, dvmMethod.methodName, vaList.formatArgs(), UnicornPointer.register(emulator, Arm64Const.UC_ARM64_REG_LR)));
                    }
                    return 0;
                }
            }
        });

        Pointer _GetStaticFieldID = svcMemory.registerSvc(new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                UnicornPointer clazz = UnicornPointer.register(emulator, Arm64Const.UC_ARM64_REG_X1);
                Pointer fieldName = UnicornPointer.register(emulator, Arm64Const.UC_ARM64_REG_X2);
                Pointer argsPointer = UnicornPointer.register(emulator, Arm64Const.UC_ARM64_REG_X3);
                String name = fieldName.getString(0);
                String args = argsPointer.getString(0);
                if (log.isDebugEnabled()) {
                    log.debug("GetStaticFieldID class=" + clazz + ", fieldName=" + name + ", args=" + args);
                }
                DvmClass dvmClass = classMap.get(clazz.toUIntPeer());
                if (dvmClass == null) {
                    throw new UnicornException();
                } else {
                    return dvmClass.getStaticFieldID(name, args);
                }
            }
        });

        Pointer _GetStaticObjectField = svcMemory.registerSvc(new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                UnicornPointer clazz = UnicornPointer.register(emulator, Arm64Const.UC_ARM64_REG_X1);
                UnicornPointer jfieldID = UnicornPointer.register(emulator, Arm64Const.UC_ARM64_REG_X2);
                if (log.isDebugEnabled()) {
                    log.debug("GetStaticObjectField clazz=" + clazz + ", jfieldID=" + jfieldID);
                }
                DvmClass dvmClass = classMap.get(clazz.toUIntPeer());
                DvmField dvmField = dvmClass == null ? null : dvmClass.getStaticField(jfieldID.toUIntPeer());
                if (dvmField == null) {
                    throw new UnicornException();
                } else {
                    DvmObject<?> obj = dvmField.getStaticObjectField();
                    if (verbose) {
                        System.out.println(String.format("JNIEnv->GetStaticObjectField(%s, %s %s => %s) was called from %s", dvmClass, dvmField.fieldName, dvmField.fieldType, obj, UnicornPointer.register(emulator, Arm64Const.UC_ARM64_REG_LR)));
                    }
                    return addLocalObject(obj);
                }
            }
        });

        Pointer _GetStaticIntField = svcMemory.registerSvc(new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                UnicornPointer clazz = UnicornPointer.register(emulator, Arm64Const.UC_ARM64_REG_X1);
                UnicornPointer jfieldID = UnicornPointer.register(emulator, Arm64Const.UC_ARM64_REG_X2);
                if (log.isDebugEnabled()) {
                    log.debug("GetStaticIntField clazz=" + clazz + ", jfieldID=" + jfieldID);
                }
                DvmClass dvmClass = classMap.get(clazz.toUIntPeer());
                DvmField dvmField = dvmClass == null ? null : dvmClass.getStaticField(jfieldID.toUIntPeer());
                if (dvmField == null) {
                    throw new UnicornException();
                } else {
                    int ret = dvmField.getStaticIntField();
                    if (verbose) {
                        System.out.println(String.format("JNIEnv->GetStaticIntField(%s, %s => 0x%x) was called from %s", dvmClass, dvmField.fieldName, ret, UnicornPointer.register(emulator, Arm64Const.UC_ARM64_REG_LR)));
                    }
                    return ret;
                }
            }
        });

        Pointer _GetStaticLongField = svcMemory.registerSvc(new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                Arm64RegisterContext context = emulator.getContext();
                UnicornPointer clazz = context.getXPointer(1);
                UnicornPointer jfieldID = context.getXPointer(2);
                if (log.isDebugEnabled()) {
                    log.debug("GetStaticLongField clazz=" + clazz + ", jfieldID=" + jfieldID);
                }
                DvmClass dvmClass = classMap.get(clazz.toUIntPeer());
                DvmField dvmField = dvmClass == null ? null : dvmClass.getStaticField(jfieldID.toUIntPeer());
                if (dvmField == null) {
                    throw new UnicornException();
                } else {
                    long ret = dvmField.getStaticLongField();
                    if (verbose) {
                        System.out.println(String.format("JNIEnv->GetStaticLongField(%s, %s => 0x%x) was called from %s", dvmClass, dvmField.fieldName, ret, UnicornPointer.register(emulator, Arm64Const.UC_ARM64_REG_LR)));
                    }
                    return ret;
                }
            }
        });

        Pointer _SetStaticLongField = svcMemory.registerSvc(new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                UnicornPointer clazz = UnicornPointer.register(emulator, Arm64Const.UC_ARM64_REG_X1);
                UnicornPointer jfieldID = UnicornPointer.register(emulator, Arm64Const.UC_ARM64_REG_X2);
                long value = ((Number) emulator.getUnicorn().reg_read(Arm64Const.UC_ARM64_REG_X3)).longValue();
                if (log.isDebugEnabled()) {
                    log.debug("SetStaticLongField clazz=" + clazz + ", jfieldID=" + jfieldID + ", value=" + value);
                }
                DvmClass dvmClass = classMap.get(clazz.toUIntPeer());
                DvmField dvmField = dvmClass == null ? null : dvmClass.getStaticField(jfieldID.toUIntPeer());
                if (dvmField == null) {
                    throw new UnicornException("dvmClass=" + dvmClass);
                } else {
                    if (verbose) {
                        System.out.println(String.format("JNIEnv->SetStaticLongField(%s, %s, 0x%x) was called from %s", dvmClass, dvmField.fieldName, value, UnicornPointer.register(emulator, Arm64Const.UC_ARM64_REG_LR)));
                    }
                    dvmField.setStaticLongField(value);
                }
                return 0;
            }
        });

        Pointer _GetStringUTFLength = svcMemory.registerSvc(new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                DvmObject<?> string = getObject(UnicornPointer.register(emulator, Arm64Const.UC_ARM64_REG_X1).toUIntPeer());
                if (log.isDebugEnabled()) {
                    log.debug("GetStringUTFLength string=" + string + ", lr=" + UnicornPointer.register(emulator, Arm64Const.UC_ARM64_REG_LR));
                }
                String value = (String) string.getValue();
                if (verbose) {
                    System.out.println(String.format("JNIEnv->GetStringUTFLength(%s) was called from %s", string, UnicornPointer.register(emulator, Arm64Const.UC_ARM64_REG_LR)));
                }
                byte[] data = value.getBytes(StandardCharsets.UTF_8);
                return data.length;
            }
        });

        Pointer _GetStringUTFChars = svcMemory.registerSvc(new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                StringObject string = getObject(UnicornPointer.register(emulator, Arm64Const.UC_ARM64_REG_X1).toUIntPeer());
                Pointer isCopy = UnicornPointer.register(emulator, Arm64Const.UC_ARM64_REG_X2);
                if (isCopy != null) {
                    isCopy.setInt(0, JNI_TRUE);
                }
                String value = string.getValue();
                if (verbose) {
                    System.out.println(String.format("JNIEnv->GetStringUtfChars(%s) was called from %s", string, UnicornPointer.register(emulator, Arm64Const.UC_ARM64_REG_LR)));
                }
                byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
                if (log.isDebugEnabled()) {
                    log.debug("GetStringUTFChars string=" + string + ", isCopy=" + isCopy + ", value=" + value + ", lr=" + UnicornPointer.register(emulator, Arm64Const.UC_ARM64_REG_LR));
                }
                byte[] data = Arrays.copyOf(bytes, bytes.length + 1);
                MemoryBlock memoryBlock = emulator.getMemory().malloc(data.length);
                memoryBlock.getPointer().write(0, data, 0, data.length);
                string.memoryBlock = memoryBlock;
                return memoryBlock.getPointer().toUIntPeer();
            }
        });

        Pointer _ReleaseStringUTFChars = svcMemory.registerSvc(new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                StringObject string = getObject(UnicornPointer.register(emulator, Arm64Const.UC_ARM64_REG_X1).toUIntPeer());
                Pointer pointer = UnicornPointer.register(emulator, Arm64Const.UC_ARM64_REG_X2);
                if (verbose) {
                    System.out.println(String.format("JNIEnv->ReleaseStringUTFChars(%s) was called from %s", string, UnicornPointer.register(emulator, Arm64Const.UC_ARM64_REG_X0)));
                }
                if (log.isDebugEnabled()) {
                    log.debug("ReleaseStringUTFChars string=" + string + ", pointer=" + pointer + ", lr=" + UnicornPointer.register(emulator, Arm64Const.UC_ARM64_REG_LR));
                }
                if (string.memoryBlock != null && string.memoryBlock.isSame(pointer)) {
                    string.memoryBlock.free(true);
                    string.memoryBlock = null;
                }
                return 0;
            }
        });

        Pointer _GetArrayLength = svcMemory.registerSvc(new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                UnicornPointer pointer = UnicornPointer.register(emulator, Arm64Const.UC_ARM64_REG_X1);
                Array<?> array = getObject(pointer.toUIntPeer());
                if (log.isDebugEnabled()) {
                    log.debug("GetArrayLength array=" + array);
                }
                if (verbose) {
                    System.out.println(String.format("JNIEnv->GetArrayLength(%s => %s) was called from %s", array, array.length(), UnicornPointer.register(emulator, Arm64Const.UC_ARM64_REG_LR)));
                }
                return array.length();
            }
        });

        Pointer _NewObjectArray = svcMemory.registerSvc(new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                RegisterContext context = emulator.getContext();
                int size = context.getIntArg(1);
                UnicornPointer elementClass = context.getPointerArg(2);
                UnicornPointer initialElement = context.getPointerArg(3);
                if (log.isDebugEnabled()) {
                    log.debug("NewObjectArray size=" + size + ", elementClass=" + elementClass + ", initialElement=" + initialElement);
                }
                DvmClass dvmClass = classMap.get(elementClass.toUIntPeer());
                if (dvmClass == null) {
                    throw new UnicornException("elementClass=" + elementClass);
                }

                DvmObject<?> obj = size == 0 ? null : getObject(initialElement.toUIntPeer());
                DvmObject<?>[] array = new DvmObject[size];
                for (int i = 0; i < size; i++) {
                    array[i] = obj;
                }

                return addObject(new ArrayObject(array), false);
            }
        });

        Pointer _GetObjectArrayElement = svcMemory.registerSvc(new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                ArrayObject array = getObject(UnicornPointer.register(emulator, Arm64Const.UC_ARM64_REG_X1).toUIntPeer());
                int index = ((Number) emulator.getUnicorn().reg_read(Arm64Const.UC_ARM64_REG_X2)).intValue();
                if (log.isDebugEnabled()) {
                    log.debug("GetObjectArrayElement array=" + array + ", index=" + index);
                }
                if (verbose) {
                    System.out.println(String.format("JNIEnv->GetObjectArrayElement(%s, %d) was called from %s", array, index, UnicornPointer.register(emulator, Arm64Const.UC_ARM64_REG_LR)));
                }
                return addObject(array.getValue()[index], false);
            }
        });

        Pointer _NewByteArray = svcMemory.registerSvc(new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                int size = ((Number) emulator.getUnicorn().reg_read(Arm64Const.UC_ARM64_REG_X1)).intValue();
                if (log.isDebugEnabled()) {
                    log.debug("NewByteArray size=" + size);
                }
                if (verbose) {
                    System.out.println(String.format("JNIEnv->NewByteArray(%d) was called from %s", size, UnicornPointer.register(emulator, Arm64Const.UC_ARM64_REG_LR)));
                }
                return addObject(new ByteArray(new byte[size]), false);
            }
        });

        Pointer _NewIntArray = svcMemory.registerSvc(new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                int size = ((Number) emulator.getUnicorn().reg_read(Arm64Const.UC_ARM64_REG_X1)).intValue();
                if (log.isDebugEnabled()) {
                    log.debug("NewIntArray size=" + size);
                }
                if (verbose) {
                    System.out.println(String.format("JNIEnv->NewIntArray(%d) was called from %s", size, UnicornPointer.register(emulator, Arm64Const.UC_ARM64_REG_LR)));
                }
                return addObject(new IntArray(new int[size]), false);
            }
        });

        Pointer _NewDoubleArray = svcMemory.registerSvc(new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                int size = ((Number) emulator.getUnicorn().reg_read(Arm64Const.UC_ARM64_REG_X1)).intValue();
                if (log.isDebugEnabled()) {
                    log.debug("_NewDoubleArray size=" + size);
                }
                if (verbose) {
                    System.out.println(String.format("JNIEnv->NewDoubleArray(%d) was called from %s", size, UnicornPointer.register(emulator, Arm64Const.UC_ARM64_REG_LR)));
                }
                return addObject(new DoubleArray(new double[size]), false);
            }
        });

        Pointer _GetByteArrayElements = svcMemory.registerSvc(new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                UnicornPointer arrayPointer = UnicornPointer.register(emulator, Arm64Const.UC_ARM64_REG_X1);
                Pointer isCopy = UnicornPointer.register(emulator, Arm64Const.UC_ARM64_REG_X2);
                if (log.isDebugEnabled()) {
                    log.debug("GetByteArrayElements arrayPointer=" + arrayPointer + ", isCopy=" + isCopy);
                }
                if (isCopy != null) {
                    isCopy.setInt(0, JNI_TRUE);
                }
                ByteArray array = getObject(arrayPointer.toUIntPeer());
                return array._GetArrayCritical(emulator, isCopy).toUIntPeer();
            }
        });

        Pointer _GetStringLength = svcMemory.registerSvc(new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                DvmObject<?> string = getObject(UnicornPointer.register(emulator, Arm64Const.UC_ARM64_REG_X1).toUIntPeer());
                if (log.isDebugEnabled()) {
                    log.debug("GetStringLength string=" + string + ", lr=" + UnicornPointer.register(emulator, Arm64Const.UC_ARM64_REG_LR));
                }
                String value = (String) string.getValue();
                return value.length();
            }
        });

        Pointer _GetStringChars = svcMemory.registerSvc(new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                StringObject string = getObject(UnicornPointer.register(emulator, Arm64Const.UC_ARM64_REG_X1).toUIntPeer());
                Pointer isCopy = UnicornPointer.register(emulator, Arm64Const.UC_ARM64_REG_X2);
                if (isCopy != null) {
                    isCopy.setInt(0, JNI_TRUE);
                }
                String value = string.getValue();
                byte[] bytes = new byte[value.length() * 2];
                ByteBuffer buffer = ByteBuffer.wrap(bytes);
                buffer.order(ByteOrder.LITTLE_ENDIAN);
                for (char c : value.toCharArray()) {
                    buffer.putChar(c);
                }
                if (log.isDebugEnabled()) {
                    log.debug("GetStringUTFChars string=" + string + ", isCopy=" + isCopy + ", value=" + value + ", lr=" + UnicornPointer.register(emulator, Arm64Const.UC_ARM64_REG_LR));
                }
                byte[] data = Arrays.copyOf(bytes, bytes.length + 1);
                MemoryBlock memoryBlock = emulator.getMemory().malloc(data.length);
                memoryBlock.getPointer().write(0, data, 0, data.length);
                string.memoryBlock = memoryBlock;
                return memoryBlock.getPointer().toUIntPeer();
            }
        });

        Pointer _ReleaseStringChars = svcMemory.registerSvc(new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                StringObject string = getObject(UnicornPointer.register(emulator, Arm64Const.UC_ARM64_REG_X1).toUIntPeer());
                Pointer pointer = UnicornPointer.register(emulator, Arm64Const.UC_ARM64_REG_X2);
                if (log.isDebugEnabled()) {
                    log.debug("ReleaseStringChars string=" + string + ", pointer=" + pointer + ", lr=" + UnicornPointer.register(emulator, Arm64Const.UC_ARM64_REG_LR));
                }
                if (string.memoryBlock != null && string.memoryBlock.isSame(pointer)) {
                    string.memoryBlock.free(true);
                    string.memoryBlock = null;
                }
                return 0;
            }
        });

        Pointer _NewStringUTF = svcMemory.registerSvc(new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                Pointer bytes = UnicornPointer.register(emulator, Arm64Const.UC_ARM64_REG_X1);
                String string = bytes.getString(0);
                if (log.isDebugEnabled()) {
                    log.debug("NewStringUTF bytes=" + bytes + ", string=" + string);
                }
                if (verbose) {
                    System.out.println(String.format("JNIEnv->NewStringUTF(\"%s\") was called from %s", string, UnicornPointer.register(emulator, Arm64Const.UC_ARM64_REG_LR)));
                }
                return addObject(new StringObject(DalvikVM64.this, string), false);
            }
        });

        Pointer _ReleaseByteArrayElements = svcMemory.registerSvc(new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                UnicornPointer arrayPointer = UnicornPointer.register(emulator, Arm64Const.UC_ARM64_REG_X1);
                Pointer pointer = UnicornPointer.register(emulator, Arm64Const.UC_ARM64_REG_X2);
                int mode = ((Number) emulator.getUnicorn().reg_read(Arm64Const.UC_ARM64_REG_X3)).intValue();
                if (log.isDebugEnabled()) {
                    log.debug("ReleaseByteArrayElements arrayPointer=" + arrayPointer + ", pointer=" + pointer + ", mode=" + mode);
                }
                ByteArray array = getObject(arrayPointer.toUIntPeer());
                array._ReleaseArrayCritical(pointer, mode);
                return 0;
            }
        });

        Pointer _GetByteArrayRegion = svcMemory.registerSvc(new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                Unicorn u = emulator.getUnicorn();
                ByteArray array = getObject(UnicornPointer.register(emulator, Arm64Const.UC_ARM64_REG_X1).toUIntPeer());
                int start = ((Number) u.reg_read(Arm64Const.UC_ARM64_REG_X2)).intValue();
                int length = ((Number) u.reg_read(Arm64Const.UC_ARM64_REG_X3)).intValue();
                Pointer buf = UnicornPointer.register(emulator, Arm64Const.UC_ARM64_REG_X4);
                if (verbose) {
                    System.out.println(String.format("JNIEnv->GetByteArrayRegion(%s, %d, %d, %s) was called from %s", array, start, length, buf, UnicornPointer.register(emulator, Arm64Const.UC_ARM64_REG_LR)));
                }
                byte[] data = Arrays.copyOfRange(array.value, start, start + length);
                if (log.isDebugEnabled()) {
                    Inspector.inspect(data, "GetByteArrayRegion array=" + array + ", start=" + start + ", length=" + length + ", buf=" + buf);
                }
                buf.write(0, data, 0, data.length);
                return 0;
            }
        });

        Pointer _SetByteArrayRegion = svcMemory.registerSvc(new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                Unicorn u = emulator.getUnicorn();
                ByteArray array = getObject(UnicornPointer.register(emulator, Arm64Const.UC_ARM64_REG_X1).toUIntPeer());
                int start = ((Number) u.reg_read(Arm64Const.UC_ARM64_REG_X2)).intValue();
                int len = ((Number) u.reg_read(Arm64Const.UC_ARM64_REG_X3)).intValue();
                Pointer buf = UnicornPointer.register(emulator, Arm64Const.UC_ARM64_REG_X4);
                if (verbose) {
                    System.out.println(String.format("JNIEnv->SetByteArrayRegion(%s, %d, %d, %s) was called from %s", array, start, len, buf, UnicornPointer.register(emulator, Arm64Const.UC_ARM64_REG_LR)));
                }
                byte[] data = buf.getByteArray(0, len);
                if (log.isDebugEnabled()) {
                    if (data.length > 1024) {
                        Inspector.inspect(Arrays.copyOf(data, 1024), "SetByteArrayRegion array=" + array + ", start=" + start + ", len=" + len + ", buf=" + buf);
                    } else {
                        Inspector.inspect(data, "SetByteArrayRegion array=" + array + ", start=" + start + ", len=" + len + ", buf=" + buf);
                    }
                }
                array.setData(start, data);
                return 0;
            }
        });

        Pointer _SetIntArrayRegion = svcMemory.registerSvc(new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                Unicorn u = emulator.getUnicorn();
                IntArray array = getObject(UnicornPointer.register(emulator, Arm64Const.UC_ARM64_REG_X1).toUIntPeer());
                int start = ((Number) u.reg_read(Arm64Const.UC_ARM64_REG_X2)).intValue();
                int len = ((Number) u.reg_read(Arm64Const.UC_ARM64_REG_X3)).intValue();
                Pointer buf = UnicornPointer.register(emulator, Arm64Const.UC_ARM64_REG_X4);
                int[] data = buf.getIntArray(0, len);
                if (log.isDebugEnabled()) {
                    log.debug("SetIntArrayRegion array=" + array + ", start=" + start + ", len=" + len + ", buf=" + buf);
                }
                array.setData(start, data);
                return 0;
            }
        });

        Pointer _SetDoubleArrayRegion = svcMemory.registerSvc(new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                Unicorn u = emulator.getUnicorn();
                DoubleArray array = getObject(UnicornPointer.register(emulator, Arm64Const.UC_ARM64_REG_X1).toUIntPeer());
                int start = ((Number) u.reg_read(Arm64Const.UC_ARM64_REG_X2)).intValue();
                int len = ((Number) u.reg_read(Arm64Const.UC_ARM64_REG_X3)).intValue();
                Pointer buf = UnicornPointer.register(emulator, Arm64Const.UC_ARM64_REG_X4);
                double[] data = buf.getDoubleArray(0, len);
                if (log.isDebugEnabled()) {
                    log.debug("SetDoubleArrayRegion array=" + array + ", start=" + start + ", len=" + len + ", buf=" + buf);
                }
                array.setData(start, data);
                return 0;
            }
        });

        Pointer _RegisterNatives = svcMemory.registerSvc(new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                UnicornPointer clazz = UnicornPointer.register(emulator, Arm64Const.UC_ARM64_REG_X1);
                Pointer methods = UnicornPointer.register(emulator, Arm64Const.UC_ARM64_REG_X2);
                int nMethods = ((Number) emulator.getUnicorn().reg_read(Arm64Const.UC_ARM64_REG_X3)).intValue();
                DvmClass dvmClass = classMap.get(clazz.toUIntPeer());
                if (log.isDebugEnabled()) {
                    log.debug("RegisterNatives dvmClass=" + dvmClass + ", methods=" + methods + ", nMethods=" + nMethods);
                }
                if (verbose) {
                    System.out.println(String.format("JNIEnv->RegisterNatives(%s, %s, %d) was called from %s", dvmClass.value, methods, nMethods, UnicornPointer.register(emulator, Arm64Const.UC_ARM64_REG_LR)));
                }
                for (int i = 0; i < nMethods; i++) {
                    Pointer method = methods.share(i * emulator.getPointerSize() * 3);
                    Pointer name = method.getPointer(0);
                    Pointer signature = method.getPointer(emulator.getPointerSize());
                    Pointer fnPtr = method.getPointer(emulator.getPointerSize() * 2);
                    String methodName = name.getString(0);
                    String signatureValue = signature.getString(0);
                    if (log.isDebugEnabled()) {
                        log.debug("RegisterNatives dvmClass=" + dvmClass + ", name=" + methodName + ", signature=" + signatureValue + ", fnPtr=" + fnPtr);
                    }
                    dvmClass.nativesMap.put(methodName + signatureValue, (UnicornPointer) fnPtr);

                    if (verbose) {
                        System.out.println(String.format("RegisterNative(%s, %s%s, %s)", dvmClass.value, methodName, signatureValue, fnPtr));
                    }
                }
                return JNI_OK;
            }
        });

        Pointer _GetJavaVM = svcMemory.registerSvc(new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                UnicornPointer vm = UnicornPointer.register(emulator, Arm64Const.UC_ARM64_REG_X1);
                if (log.isDebugEnabled()) {
                    log.debug("GetJavaVM vm=" + vm);
                }
                vm.setPointer(0, _JavaVM);
                return JNI_OK;
            }
        });

        Pointer _ExceptionCheck = svcMemory.registerSvc(new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                if (log.isDebugEnabled()) {
                    log.debug("ExceptionCheck throwable=" + throwable);
                }
                return throwable == null ? JNI_FALSE : JNI_TRUE;
            }
        });

        Pointer _GetObjectRefType = svcMemory.registerSvc(new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                UnicornPointer object = UnicornPointer.register(emulator, Arm64Const.UC_ARM64_REG_X1);
                DvmObject<?> dvmGlobalObject = globalObjectMap.get(object.toUIntPeer());
                DvmObject<?> dvmLocalObject = localObjectMap.get(object.toUIntPeer());
                if (log.isDebugEnabled()) {
                    log.debug("GetObjectRefType object=" + object + ", dvmGlobalObject=" + dvmGlobalObject + ", dvmLocalObject=" + dvmLocalObject);
                }
                if (dvmGlobalObject != null) {
                    return JNIGlobalRefType;
                } else if(dvmLocalObject != null) {
                    return JNILocalRefType;
                } else {
                    return JNIInvalidRefType;
                }
            }
        });

        final UnicornPointer impl = svcMemory.allocate(0xE9 * emulator.getPointerSize(), "JNIEnv.impl");
        for (int i = 0; i < 0xE9 * emulator.getPointerSize(); i += emulator.getPointerSize()) {
            impl.setLong(i, i);
        }
        impl.setPointer(0x30, _FindClass);
        impl.setPointer(0x48, _ToReflectedMethod);
        impl.setPointer(0x68, _Throw);
        impl.setPointer(0x78, _ExceptionOccurred);
        impl.setPointer(0x88, _ExceptionClear);
        impl.setPointer(0x98, _PushLocalFrame);
        impl.setPointer(0xA0, _PopLocalFrame);
        impl.setPointer(0xa8, _NewGlobalRef);
        impl.setPointer(0xB0, _DeleteGlobalRef);
        impl.setPointer(0xB8, _DeleteLocalRef);
        impl.setPointer(0xC0, _IsSameObject);
        impl.setPointer(0xC8, _NewLocalRef);
        impl.setPointer(0xd0, _EnsureLocalCapacity);
        impl.setPointer(0xE0, _NewObject);
        impl.setPointer(0xE8, _NewObjectV);
        impl.setPointer(0xF8, _GetObjectClass);
        impl.setPointer(0x100, _IsInstanceOf);
        impl.setPointer(0x108, _GetMethodID);
        impl.setPointer(0x110, _CallObjectMethod);
        impl.setPointer(0x118, _CallObjectMethodV);
        impl.setPointer(0x128, _CallBooleanMethod);
        impl.setPointer(0x130, _CallBooleanMethodV);
        impl.setPointer(0x188, _CallIntMethod);
        impl.setPointer(0x190, _CallIntMethodV);
        impl.setPointer(0x1a8, _CallLongMethodV);
        impl.setPointer(0x1c0, _CallFloatMethodV);
        impl.setPointer(0x1e8, _CallVoidMethod);
        impl.setPointer(0x1f0, _CallVoidMethodV);
        impl.setPointer(0x2f0, _GetFieldID);
        impl.setPointer(0x2F8, _GetObjectField);
        impl.setPointer(0x300, _GetBooleanField);
        impl.setPointer(0x320, _GetIntField);
        impl.setPointer(0x328, _GetLongField);
        impl.setPointer(0x340, _SetObjectField);
        impl.setPointer(0x348, _SetBooleanField);
        impl.setPointer(0x368, _SetIntField);
        impl.setPointer(0x370, _SetLongField);
        impl.setPointer(0x380, _SetDoubleField);
        impl.setPointer(0x388, _GetStaticMethodID);
        impl.setPointer(0x390, _CallStaticObjectMethod);
        impl.setPointer(0x398, _CallStaticObjectMethodV);
        impl.setPointer(0x3A8, _CallStaticBooleanMethod);
        impl.setPointer(0x3B0, _CallStaticBooleanMethodV);
        impl.setPointer(0x408, _CallStaticIntMethod);
        impl.setPointer(0x410, _CallStaticIntMethodV);
        impl.setPointer(0x428, _CallStaticLongMethodV);
        impl.setPointer(0x468, _CallStaticVoidMethod);
        impl.setPointer(0x470, _CallStaticVoidMethodV);
        impl.setPointer(0x480, _GetStaticFieldID);
        impl.setPointer(0x488, _GetStaticObjectField);
        impl.setPointer(0x4B0, _GetStaticIntField);
        impl.setPointer(0x4B8, _GetStaticLongField);
        impl.setPointer(0x500, _SetStaticLongField);
        impl.setPointer(0x540, _GetStringUTFLength);
        impl.setPointer(0x548, _GetStringUTFChars);
        impl.setPointer(0x550, _ReleaseStringUTFChars);
        impl.setPointer(0x558, _GetArrayLength);
        impl.setPointer(0x560, _NewObjectArray);
        impl.setPointer(0x580, _NewByteArray);
        impl.setPointer(0x598, _NewIntArray);
        impl.setPointer(0x5b0, _NewDoubleArray);
        impl.setPointer(0x5c0, _GetByteArrayElements);
        impl.setPointer(0x520, _GetStringLength);
        impl.setPointer(0x528, _GetStringChars);
        impl.setPointer(0x530, _ReleaseStringChars);
        impl.setPointer(0x538, _NewStringUTF);
        impl.setPointer(0x568, _GetObjectArrayElement);
        impl.setPointer(0x600, _ReleaseByteArrayElements);
        impl.setPointer(0x640, _GetByteArrayRegion);
        impl.setPointer(0x680, _SetByteArrayRegion);
        impl.setPointer(0x698, _SetIntArrayRegion);
        impl.setPointer(0x6B0, _SetDoubleArrayRegion);
        impl.setPointer(0x6B8, _RegisterNatives);
        impl.setPointer(0x6D8, _GetJavaVM);
        impl.setPointer(0x720, _ExceptionCheck);
        impl.setPointer(0x740, _GetObjectRefType);

        _JNIEnv = svcMemory.allocate(emulator.getPointerSize(), "_JNIEnv");
        _JNIEnv.setPointer(0, impl);

        UnicornPointer _AttachCurrentThread = svcMemory.registerSvc(new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                Pointer vm = UnicornPointer.register(emulator, Arm64Const.UC_ARM64_REG_X0);
                Pointer env = UnicornPointer.register(emulator, Arm64Const.UC_ARM64_REG_X1);
                Pointer args = UnicornPointer.register(emulator, Arm64Const.UC_ARM64_REG_X2); // JavaVMAttachArgs*
                if (log.isDebugEnabled()) {
                    log.debug("AttachCurrentThread vm=" + vm + ", env=" + env.getPointer(0) + ", args=" + args);
                }
                env.setPointer(0, _JNIEnv);
                return JNI_OK;
            }
        });

        UnicornPointer _GetEnv = svcMemory.registerSvc(new Arm64Svc() {
            @Override
            public long handle(Emulator<?> emulator) {
                Pointer vm = UnicornPointer.register(emulator, Arm64Const.UC_ARM64_REG_X0);
                Pointer env = UnicornPointer.register(emulator, Arm64Const.UC_ARM64_REG_X1);
                int version = ((Number) emulator.getUnicorn().reg_read(Arm64Const.UC_ARM64_REG_X2)).intValue();
                if (log.isDebugEnabled()) {
                    log.debug("GetEnv vm=" + vm + ", env=" + env.getPointer(0) + ", version=0x" + Integer.toHexString(version));
                }
                env.setPointer(0, _JNIEnv);
                return JNI_OK;
            }
        });

        UnicornPointer _JNIInvokeInterface = svcMemory.allocate(emulator.getPointerSize() * 8, "_JNIInvokeInterface");
        for (int i = 0; i < emulator.getPointerSize() * 8; i += emulator.getPointerSize()) {
            _JNIInvokeInterface.setInt(i, i);
        }
        _JNIInvokeInterface.setPointer(emulator.getPointerSize() * 4, _AttachCurrentThread);
        _JNIInvokeInterface.setPointer(emulator.getPointerSize() * 6, _GetEnv);

        _JavaVM.setPointer(0, _JNIInvokeInterface);

        if (log.isDebugEnabled()) {
            log.debug("_JavaVM=" + _JavaVM + ", _JNIInvokeInterface=" + _JNIInvokeInterface + ", _JNIEnv=" + _JNIEnv);
        }
    }

    @Override
    public Pointer getJavaVM() {
        return _JavaVM;
    }

    @Override
    public Pointer getJNIEnv() {
        return _JNIEnv;
    }

    byte[] findLibrary(ApkFile apkFile, String soName) throws IOException {
        byte[] soData = apkFile.getFileData("lib/arm64-v8a/" + soName);
        if (soData != null) {
            log.debug("resolve arm64-v8a library: " + soName);
            return soData;
        } else {
            return null;
        }
    }
}
