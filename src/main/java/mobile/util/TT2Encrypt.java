package mobile.util;

import com.alibaba.fastjson.JSON;
import com.google.common.collect.Lists;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPOutputStream;

public class TT2Encrypt {

    private Integer CF = 0;

    public TT2Encrypt(long[] content, String model) {
        if (StringUtils.isEmpty(model)) {
            model = "octet";
        }

        this.model = model;
        this.content = content;
        this.list_9C8 = hex_9C8();
        this.beginning = beginning;


        long[] bbs = new long[]{0xf3bcc908, 0x6a09e667, 0x84caa73b, 0xbb67ae85, 0xfe94f82b, 0x3c6ef372, 0x5f1d36f1, 0xa54ff53a,
                0xade682d1, 0x510e527f, 0x2b3e6c1f, 0x9b05688c, 0xfb41bd6b, 0x1f83d9ab, 0x137e2179, 0x5be0cd19};

        this.list_6B0 = convertZhengLong(bbs);
    }


    public static long check(Long tmp) {
        String ss;
        if (tmp < 0) {
            ss = Long.toHexString(0x100000000L + tmp);
        } else {
            ss = Long.toHexString(tmp);
        }
        if (ss.length() > 8) {
            int size = ss.length();
            int start = size - 8;
            ss = ss.substring(start, size);
            tmp = Long.parseLong(ss, 16);
        }
        return tmp;
    }

    public long flip(long num) {
        String result = "";
        String lst = Long.toHexString(num);

        for (int index = 0; index < 8; index++) {
            if (index < lst.length()) {
                result = result + lst.charAt(index);
            } else {
                result = "0" + result;
            }
        }

        return Long.parseLong(result.substring(6, 8) + result.substring(4, 6) + result.substring(2, 4) + result.substring(0, 2), 16);
    }

    public long[] flip_list(long[] content) {
        List<Long> result = new ArrayList<>();
        long[] tmp_list = dump_list(content);
        for (int i = 0; i < tmp_list.length; i++) {
            result.add(flip(tmp_list[i]));
        }
        return result.stream().mapToLong(Long::valueOf).toArray();
    }

    public long[] dump_list(long[] content) {
        int size = content.length;
        int ssize = (size / 4);
        long[] result = new long[ssize];
        for (int index = 0; index < ssize; index++) {
            String tmp_string = "";
            for (int j = 0; j < 4; j++) {
                String tmp = Long.toHexString(content[4 * index + j]);
                if (tmp.length() < 2) {
                    tmp = "0" + tmp;
                }
                tmp_string = tmp_string + tmp;
            }
            long i = Long.parseLong(tmp_string, 16);
            result[index] = i;
        }
        return result;
    }

    public long[] hex_list(long[] content) {
        List<Long> result = new ArrayList<Long>();
        for (long item : content) {
            String tmp = Long.toHexString(item);
            while (tmp.length() < 8) {
                tmp = "0" + tmp;
            }
            for (int index = 0; index < 4; index++) {
                int start = 2 * index;
                int end = 2 * index + 2;
                String ss = tmp.substring(start, end);
                result.add(Long.parseLong(ss, 16));
            }
        }
        long[] array = result.stream().mapToLong(Long::valueOf).toArray();
        return array;
    }

    public String bin_type(long num) {
        String result = "";

        num = check(num);

        String lst = Long.toBinaryString(num);

        for (int index = 0; index < 32; index++) {
            if (index < lst.length()) {
                result = result + lst.charAt(index);
            } else {
                result = "0" + result;
            }
        }
        return result;
    }

    public long ADC(long A, long B) {
        long C = check(A) + check(B);
        long D = check(C + CF);
        return D;
    }

    public long ADCS(long A, long B) {
        long C = check(A) + check(B);
        long D = check(C + CF);
        if (Long.toHexString(C).length() > 8) {
            CF = 1;
        } else {
            CF = 0;
        }
        return D;
    }

    public long ADDS(long A, long B) {
        long C = check(A) + check(B);
        if (Long.toHexString(C).length() > 8) {
            CF = 1;
        } else {
            CF = 0;
        }
        return check(C);
    }

    public long LSLS(long num, int k) {

        String result = bin_type(num);
        CF = Integer.valueOf(String.valueOf(result.charAt(k - 1)));
        //System.out.println("LSLS CF current Value :" + CF);

        return check(check(num) << k);
    }

    public long LSRS(long num, int k) {

        String result = bin_type(num);
        CF = Integer.valueOf(String.valueOf(result.charAt(result.length() - k)));
        return check(check(num) >> k);
    }

    public long ANDS(long A, long B) {
        return check(A & B);
    }

    public long ORRS(long A, long B) {
        return check(A | B);
    }

    public long EORS(long A, long B) {
        return check(A ^ B);
    }

    public long ror(long num, int k) {
        String result = bin_type(num);

        int length = result.length();

        String prefix = result.substring(length - k);
        String suffix = result.substring(0, length - k);

        return Long.parseLong(prefix + suffix, 2);
    }

    public long RRX(long num) {
        String result = bin_type(num);
        int length = result.length();
        String s = CF + result.substring(0, length - 1);
        return Long.parseLong(s, 2);
    }

    public long UBFX(long num, int lsb, int width) {
        String tmp_string = Long.toBinaryString(num);
        while (tmp_string.length() < 32) {
            tmp_string = "0" + tmp_string;
        }
        int len = tmp_string.length();
        int start = len - lsb - width;
        int end = len - lsb;
        return Long.parseLong(tmp_string.substring(start, end), 2);
    }

    public long UTFX(long num) {
        String tmp_string = Long.toBinaryString(num);
        int start = tmp_string.length() - 8;
        return Long.parseLong(tmp_string.substring(start), 2);
    }

    private long[] convertZhengLong(long[] data) {
        long[] result = new long[data.length];
        for (int i = 0; i < data.length; i++) {
            long bb = data[i];
            if (bb < 0) {
                result[i] = Long.valueOf("4294967296") + bb;
            } else {
                result[i] = bb;
            }
        }
        return result;
    }


    private long[] rodata = convertZhengLong(new long[]{0xd728ae22, 0x428a2f98, 0x23ef65cd, 0x71374491, 0xec4d3b2f, 0xb5c0fbcf, 0x8189dbbc, 0xe9b5dba5, 0xf348b538,
            0x3956c25b, 0xb605d019, 0x59f111f1, 0xaf194f9b, 0x923f82a4, 0xda6d8118, 0xab1c5ed5, 0xa3030242, 0xd807aa98,
            0x45706fbe, 0x12835b01, 0x4ee4b28c, 0x243185be, 0xd5ffb4e2, 0x550c7dc3, 0xf27b896f, 0x72be5d74, 0x3b1696b1,
            0x80deb1fe, 0x25c71235, 0x9bdc06a7, 0xcf692694, 0xc19bf174, 0x9ef14ad2, 0xe49b69c1, 0x384f25e3, 0xefbe4786,
            0x8b8cd5b5, 0xfc19dc6, 0x77ac9c65, 0x240ca1cc, 0x592b0275, 0x2de92c6f, 0x6ea6e483, 0x4a7484aa, 0xbd41fbd4,
            0x5cb0a9dc, 0x831153b5, 0x76f988da, 0xee66dfab, 0x983e5152, 0x2db43210, 0xa831c66d, 0x98fb213f, 0xb00327c8,
            0xbeef0ee4, 0xbf597fc7, 0x3da88fc2, 0xc6e00bf3, 0x930aa725, 0xd5a79147, 0xe003826f, 0x6ca6351, 0xa0e6e70,
            0x14292967, 0x46d22ffc, 0x27b70a85, 0x5c26c926, 0x2e1b2138, 0x5ac42aed, 0x4d2c6dfc, 0x9d95b3df, 0x53380d13,
            0x8baf63de, 0x650a7354, 0x3c77b2a8, 0x766a0abb, 0x47edaee6, 0x81c2c92e, 0x1482353b, 0x92722c85, 0x4cf10364,
            0xa2bfe8a1, 0xbc423001, 0xa81a664b, 0xd0f89791, 0xc24b8b70, 0x654be30, 0xc76c51a3, 0xd6ef5218, 0xd192e819,
            0x5565a910, 0xd6990624, 0x5771202a, 0xf40e3585, 0x32bbd1b8, 0x106aa070, 0xb8d2d0c8, 0x19a4c116, 0x5141ab53,
            0x1e376c08, 0xdf8eeb99, 0x2748774c, 0xe19b48a8, 0x34b0bcb5, 0xc5c95a63, 0x391c0cb3, 0xe3418acb, 0x4ed8aa4a,
            0x7763e373, 0x5b9cca4f, 0xd6b2b8a3, 0x682e6ff3, 0x5defb2fc, 0x748f82ee, 0x43172f60, 0x78a5636f, 0xa1f0ab72,
            0x84c87814, 0x1a6439ec, 0x8cc70208, 0x23631e28, 0x90befffa, 0xde82bde9, 0xa4506ceb, 0xb2c67915, 0xbef9a3f7,
            0xe372532b, 0xc67178f2, 0xea26619c, 0xca273ece, 0x21c0c207, 0xd186b8c7, 0xcde0eb1e, 0xeada7dd6, 0xee6ed178,
            0xf57d4f7f, 0x72176fba, 0x6f067aa, 0xa2c898a6, 0xa637dc5, 0xbef90dae, 0x113f9804, 0x131c471b, 0x1b710b35,
            0x23047d84, 0x28db77f5, 0x40c72493, 0x32caab7b, 0x15c9bebc, 0x3c9ebe0a, 0x9c100d4c, 0x431d67c4, 0xcb3e42b6,
            0x4cc5d4be, 0xfc657e2a, 0x597f299c, 0x3ad6faec, 0x5fcb6fab, 0x4a475817, 0x6c44198c});

    private long[] dword_0 = convertZhengLong(new long[]{0x63, 0x7C, 0x77, 0x7B, 0xF2, 0x6B, 0x6F, 0xC5, 0x30, 0x01, 0x67, 0x2B, 0xFE, 0xD7, 0xAB, 0x76,
            0xCA, 0x82, 0xC9, 0x7D, 0xFA, 0x59, 0x47, 0xF0, 0xAD, 0xD4, 0xA2, 0xAF, 0x9C, 0xA4, 0x72, 0xC0,
            0xB7, 0xFD, 0x93, 0x26, 0x36, 0x3F, 0xF7, 0xCC, 0x34, 0xA5, 0xE5, 0xF1, 0x71, 0xD8, 0x31, 0x15,
            0x04, 0xC7, 0x23, 0xC3, 0x18, 0x96, 0x05, 0x9A, 0x07, 0x12, 0x80, 0xE2, 0xEB, 0x27, 0xB2, 0x75,
            0x09, 0x83, 0x2C, 0x1A, 0x1B, 0x6E, 0x5A, 0xA0, 0x52, 0x3B, 0xD6, 0xB3, 0x29, 0xE3, 0x2F, 0x84,
            0x53, 0xD1, 0x00, 0xED, 0x20, 0xFC, 0xB1, 0x5B, 0x6A, 0xCB, 0xBE, 0x39, 0x4A, 0x4C, 0x58, 0xCF,
            0xD0, 0xEF, 0xAA, 0xFB, 0x43, 0x4D, 0x33, 0x85, 0x45, 0xF9, 0x02, 0x7F, 0x50, 0x3C, 0x9F, 0xA8,
            0x51, 0xA3, 0x40, 0x8F, 0x92, 0x9D, 0x38, 0xF5, 0xBC, 0xB6, 0xDA, 0x21, 0x10, 0xFF, 0xF3, 0xD2,
            0xCD, 0x0C, 0x13, 0xEC, 0x5F, 0x97, 0x44, 0x17, 0xC4, 0xA7, 0x7E, 0x3D, 0x64, 0x5D, 0x19, 0x73,
            0x60, 0x81, 0x4F, 0xDC, 0x22, 0x2A, 0x90, 0x88, 0x46, 0xEE, 0xB8, 0x14, 0xDE, 0x5E, 0x0B, 0xDB,
            0xE0, 0x32, 0x3A, 0x0A, 0x49, 0x06, 0x24, 0x5C, 0xC2, 0xD3, 0xAC, 0x62, 0x91, 0x95, 0xE4, 0x79,
            0xE7, 0xC8, 0x37, 0x6D, 0x8D, 0xD5, 0x4E, 0xA9, 0x6C, 0x56, 0xF4, 0xEA, 0x65, 0x7A, 0xAE, 0x08,
            0xBA, 0x78, 0x25, 0x2E, 0x1C, 0xA6, 0xB4, 0xC6, 0xE8, 0xDD, 0x74, 0x1F, 0x4B, 0xBD, 0x8B, 0x8A,
            0x70, 0x3E, 0xB5, 0x66, 0x48, 0x03, 0xF6, 0x0E, 0x61, 0x35, 0x57, 0xB9, 0x86, 0xC1, 0x1D, 0x9E,
            0xE1, 0xF8, 0x98, 0x11, 0x69, 0xD9, 0x8E, 0x94, 0x9B, 0x1E, 0x87, 0xE9, 0xCE, 0x55, 0x28, 0xDF,
            0x8C, 0xA1, 0x89, 0x0D, 0xBF, 0xE6, 0x42, 0x68, 0x41, 0x99, 0x2D, 0x0F, 0xB0, 0x54, 0xBB, 0x16});

    private long[] dword_1 = convertZhengLong(new long[]{0x1000000, 0x2000000, 0x4000000, 0x8000000, 0x10000000, 0x20000000,
            0x40000000, 0x80000000, 0x1B000000, 0x36000000});

    private long[] dword_2 = convertZhengLong(new long[]{0x0, 0xe090d0b, 0x1c121a16, 0x121b171d, 0x3824342c, 0x362d3927, 0x24362e3a, 0x2a3f2331, 0x70486858,
            0x7e416553, 0x6c5a724e, 0x62537f45, 0x486c5c74, 0x4665517f, 0x547e4662, 0x5a774b69, 0xe090d0b0, 0xee99ddbb,
            0xfc82caa6, 0xf28bc7ad, 0xd8b4e49c, 0xd6bde997, 0xc4a6fe8a, 0xcaaff381, 0x90d8b8e8, 0x9ed1b5e3, 0x8ccaa2fe,
            0x82c3aff5, 0xa8fc8cc4, 0xa6f581cf, 0xb4ee96d2, 0xbae79bd9, 0xdb3bbb7b, 0xd532b670, 0xc729a16d, 0xc920ac66,
            0xe31f8f57, 0xed16825c, 0xff0d9541, 0xf104984a, 0xab73d323, 0xa57ade28, 0xb761c935, 0xb968c43e, 0x9357e70f,
            0x9d5eea04, 0x8f45fd19, 0x814cf012, 0x3bab6bcb, 0x35a266c0, 0x27b971dd, 0x29b07cd6, 0x38f5fe7, 0xd8652ec,
            0x1f9d45f1, 0x119448fa, 0x4be30393, 0x45ea0e98, 0x57f11985, 0x59f8148e, 0x73c737bf, 0x7dce3ab4, 0x6fd52da9,
            0x61dc20a2, 0xad766df6, 0xa37f60fd, 0xb16477e0, 0xbf6d7aeb, 0x955259da, 0x9b5b54d1, 0x894043cc, 0x87494ec7,
            0xdd3e05ae, 0xd33708a5, 0xc12c1fb8, 0xcf2512b3, 0xe51a3182, 0xeb133c89, 0xf9082b94, 0xf701269f, 0x4de6bd46,
            0x43efb04d, 0x51f4a750, 0x5ffdaa5b, 0x75c2896a, 0x7bcb8461, 0x69d0937c, 0x67d99e77, 0x3daed51e, 0x33a7d815,
            0x21bccf08, 0x2fb5c203, 0x58ae132, 0xb83ec39, 0x1998fb24, 0x1791f62f, 0x764dd68d, 0x7844db86, 0x6a5fcc9b,
            0x6456c190, 0x4e69e2a1, 0x4060efaa, 0x527bf8b7, 0x5c72f5bc, 0x605bed5, 0x80cb3de, 0x1a17a4c3, 0x141ea9c8,
            0x3e218af9, 0x302887f2, 0x223390ef, 0x2c3a9de4, 0x96dd063d, 0x98d40b36, 0x8acf1c2b, 0x84c61120, 0xaef93211,
            0xa0f03f1a, 0xb2eb2807, 0xbce2250c, 0xe6956e65, 0xe89c636e, 0xfa877473, 0xf48e7978, 0xdeb15a49, 0xd0b85742,
            0xc2a3405f, 0xccaa4d54, 0x41ecdaf7, 0x4fe5d7fc, 0x5dfec0e1, 0x53f7cdea, 0x79c8eedb, 0x77c1e3d0, 0x65daf4cd,
            0x6bd3f9c6, 0x31a4b2af, 0x3fadbfa4, 0x2db6a8b9, 0x23bfa5b2, 0x9808683, 0x7898b88, 0x15929c95, 0x1b9b919e,
            0xa17c0a47, 0xaf75074c, 0xbd6e1051, 0xb3671d5a, 0x99583e6b, 0x97513360, 0x854a247d, 0x8b432976, 0xd134621f,
            0xdf3d6f14, 0xcd267809, 0xc32f7502, 0xe9105633, 0xe7195b38, 0xf5024c25, 0xfb0b412e, 0x9ad7618c, 0x94de6c87,
            0x86c57b9a, 0x88cc7691, 0xa2f355a0, 0xacfa58ab, 0xbee14fb6, 0xb0e842bd, 0xea9f09d4, 0xe49604df, 0xf68d13c2,
            0xf8841ec9, 0xd2bb3df8, 0xdcb230f3, 0xcea927ee, 0xc0a02ae5, 0x7a47b13c, 0x744ebc37, 0x6655ab2a, 0x685ca621,
            0x42638510, 0x4c6a881b, 0x5e719f06, 0x5078920d, 0xa0fd964, 0x406d46f, 0x161dc372, 0x1814ce79, 0x322bed48,
            0x3c22e043, 0x2e39f75e, 0x2030fa55, 0xec9ab701, 0xe293ba0a, 0xf088ad17, 0xfe81a01c, 0xd4be832d, 0xdab78e26,
            0xc8ac993b, 0xc6a59430, 0x9cd2df59, 0x92dbd252, 0x80c0c54f, 0x8ec9c844, 0xa4f6eb75, 0xaaffe67e, 0xb8e4f163,
            0xb6edfc68, 0xc0a67b1, 0x2036aba, 0x10187da7, 0x1e1170ac, 0x342e539d, 0x3a275e96, 0x283c498b, 0x26354480,
            0x7c420fe9, 0x724b02e2, 0x605015ff, 0x6e5918f4, 0x44663bc5, 0x4a6f36ce, 0x587421d3, 0x567d2cd8, 0x37a10c7a,
            0x39a80171, 0x2bb3166c, 0x25ba1b67, 0xf853856, 0x18c355d, 0x13972240, 0x1d9e2f4b, 0x47e96422, 0x49e06929,
            0x5bfb7e34, 0x55f2733f, 0x7fcd500e, 0x71c45d05, 0x63df4a18, 0x6dd64713, 0xd731dcca, 0xd938d1c1, 0xcb23c6dc,
            0xc52acbd7, 0xef15e8e6, 0xe11ce5ed, 0xf307f2f0, 0xfd0efffb, 0xa779b492, 0xa970b999, 0xbb6bae84, 0xb562a38f,
            0x9f5d80be, 0x91548db5, 0x834f9aa8, 0x8d4697a3});

    private long[] dword_3 = convertZhengLong(new long[]{0x0, 0xb0e090d, 0x161c121a, 0x1d121b17, 0x2c382434, 0x27362d39, 0x3a24362e, 0x312a3f23, 0x58704868,
            0x537e4165, 0x4e6c5a72, 0x4562537f, 0x74486c5c, 0x7f466551, 0x62547e46, 0x695a774b, 0xb0e090d0, 0xbbee99dd,
            0xa6fc82ca, 0xadf28bc7, 0x9cd8b4e4, 0x97d6bde9, 0x8ac4a6fe, 0x81caaff3, 0xe890d8b8, 0xe39ed1b5, 0xfe8ccaa2,
            0xf582c3af, 0xc4a8fc8c, 0xcfa6f581, 0xd2b4ee96, 0xd9bae79b, 0x7bdb3bbb, 0x70d532b6, 0x6dc729a1, 0x66c920ac,
            0x57e31f8f, 0x5ced1682, 0x41ff0d95, 0x4af10498, 0x23ab73d3, 0x28a57ade, 0x35b761c9, 0x3eb968c4, 0xf9357e7,
            0x49d5eea, 0x198f45fd, 0x12814cf0, 0xcb3bab6b, 0xc035a266, 0xdd27b971, 0xd629b07c, 0xe7038f5f, 0xec0d8652,
            0xf11f9d45, 0xfa119448, 0x934be303, 0x9845ea0e, 0x8557f119, 0x8e59f814, 0xbf73c737, 0xb47dce3a, 0xa96fd52d,
            0xa261dc20, 0xf6ad766d, 0xfda37f60, 0xe0b16477, 0xebbf6d7a, 0xda955259, 0xd19b5b54, 0xcc894043, 0xc787494e,
            0xaedd3e05, 0xa5d33708, 0xb8c12c1f, 0xb3cf2512, 0x82e51a31, 0x89eb133c, 0x94f9082b, 0x9ff70126, 0x464de6bd,
            0x4d43efb0, 0x5051f4a7, 0x5b5ffdaa, 0x6a75c289, 0x617bcb84, 0x7c69d093, 0x7767d99e, 0x1e3daed5, 0x1533a7d8,
            0x821bccf, 0x32fb5c2, 0x32058ae1, 0x390b83ec, 0x241998fb, 0x2f1791f6, 0x8d764dd6, 0x867844db, 0x9b6a5fcc,
            0x906456c1, 0xa14e69e2, 0xaa4060ef, 0xb7527bf8, 0xbc5c72f5, 0xd50605be, 0xde080cb3, 0xc31a17a4, 0xc8141ea9,
            0xf93e218a, 0xf2302887, 0xef223390, 0xe42c3a9d, 0x3d96dd06, 0x3698d40b, 0x2b8acf1c, 0x2084c611, 0x11aef932,
            0x1aa0f03f, 0x7b2eb28, 0xcbce225, 0x65e6956e, 0x6ee89c63, 0x73fa8774, 0x78f48e79, 0x49deb15a, 0x42d0b857,
            0x5fc2a340, 0x54ccaa4d, 0xf741ecda, 0xfc4fe5d7, 0xe15dfec0, 0xea53f7cd, 0xdb79c8ee, 0xd077c1e3, 0xcd65daf4,
            0xc66bd3f9, 0xaf31a4b2, 0xa43fadbf, 0xb92db6a8, 0xb223bfa5, 0x83098086, 0x8807898b, 0x9515929c, 0x9e1b9b91,
            0x47a17c0a, 0x4caf7507, 0x51bd6e10, 0x5ab3671d, 0x6b99583e, 0x60975133, 0x7d854a24, 0x768b4329, 0x1fd13462,
            0x14df3d6f, 0x9cd2678, 0x2c32f75, 0x33e91056, 0x38e7195b, 0x25f5024c, 0x2efb0b41, 0x8c9ad761, 0x8794de6c,
            0x9a86c57b, 0x9188cc76, 0xa0a2f355, 0xabacfa58, 0xb6bee14f, 0xbdb0e842, 0xd4ea9f09, 0xdfe49604, 0xc2f68d13,
            0xc9f8841e, 0xf8d2bb3d, 0xf3dcb230, 0xeecea927, 0xe5c0a02a, 0x3c7a47b1, 0x37744ebc, 0x2a6655ab, 0x21685ca6,
            0x10426385, 0x1b4c6a88, 0x65e719f, 0xd507892, 0x640a0fd9, 0x6f0406d4, 0x72161dc3, 0x791814ce, 0x48322bed,
            0x433c22e0, 0x5e2e39f7, 0x552030fa, 0x1ec9ab7, 0xae293ba, 0x17f088ad, 0x1cfe81a0, 0x2dd4be83, 0x26dab78e,
            0x3bc8ac99, 0x30c6a594, 0x599cd2df, 0x5292dbd2, 0x4f80c0c5, 0x448ec9c8, 0x75a4f6eb, 0x7eaaffe6, 0x63b8e4f1,
            0x68b6edfc, 0xb10c0a67, 0xba02036a, 0xa710187d, 0xac1e1170, 0x9d342e53, 0x963a275e, 0x8b283c49, 0x80263544,
            0xe97c420f, 0xe2724b02, 0xff605015, 0xf46e5918, 0xc544663b, 0xce4a6f36, 0xd3587421, 0xd8567d2c, 0x7a37a10c,
            0x7139a801, 0x6c2bb316, 0x6725ba1b, 0x560f8538, 0x5d018c35, 0x40139722, 0x4b1d9e2f, 0x2247e964, 0x2949e069,
            0x345bfb7e, 0x3f55f273, 0xe7fcd50, 0x571c45d, 0x1863df4a, 0x136dd647, 0xcad731dc, 0xc1d938d1, 0xdccb23c6,
            0xd7c52acb, 0xe6ef15e8, 0xede11ce5, 0xf0f307f2, 0xfbfd0eff, 0x92a779b4, 0x99a970b9, 0x84bb6bae, 0x8fb562a3,
            0xbe9f5d80, 0xb591548d, 0xa8834f9a, 0xa38d4697});

    private long[] dword_4 = convertZhengLong(new long[]{0x0, 0xd0b0e09, 0x1a161c12, 0x171d121b, 0x342c3824, 0x3927362d, 0x2e3a2436, 0x23312a3f, 0x68587048,
            0x65537e41, 0x724e6c5a, 0x7f456253, 0x5c74486c, 0x517f4665, 0x4662547e, 0x4b695a77, 0xd0b0e090, 0xddbbee99,
            0xcaa6fc82, 0xc7adf28b, 0xe49cd8b4, 0xe997d6bd, 0xfe8ac4a6, 0xf381caaf, 0xb8e890d8, 0xb5e39ed1, 0xa2fe8cca,
            0xaff582c3, 0x8cc4a8fc, 0x81cfa6f5, 0x96d2b4ee, 0x9bd9bae7, 0xbb7bdb3b, 0xb670d532, 0xa16dc729, 0xac66c920,
            0x8f57e31f, 0x825ced16, 0x9541ff0d, 0x984af104, 0xd323ab73, 0xde28a57a, 0xc935b761, 0xc43eb968, 0xe70f9357,
            0xea049d5e, 0xfd198f45, 0xf012814c, 0x6bcb3bab, 0x66c035a2, 0x71dd27b9, 0x7cd629b0, 0x5fe7038f, 0x52ec0d86,
            0x45f11f9d, 0x48fa1194, 0x3934be3, 0xe9845ea, 0x198557f1, 0x148e59f8, 0x37bf73c7, 0x3ab47dce, 0x2da96fd5,
            0x20a261dc, 0x6df6ad76, 0x60fda37f, 0x77e0b164, 0x7aebbf6d, 0x59da9552, 0x54d19b5b, 0x43cc8940, 0x4ec78749,
            0x5aedd3e, 0x8a5d337, 0x1fb8c12c, 0x12b3cf25, 0x3182e51a, 0x3c89eb13, 0x2b94f908, 0x269ff701, 0xbd464de6,
            0xb04d43ef, 0xa75051f4, 0xaa5b5ffd, 0x896a75c2, 0x84617bcb, 0x937c69d0, 0x9e7767d9, 0xd51e3dae, 0xd81533a7,
            0xcf0821bc, 0xc2032fb5, 0xe132058a, 0xec390b83, 0xfb241998, 0xf62f1791, 0xd68d764d, 0xdb867844, 0xcc9b6a5f,
            0xc1906456, 0xe2a14e69, 0xefaa4060, 0xf8b7527b, 0xf5bc5c72, 0xbed50605, 0xb3de080c, 0xa4c31a17, 0xa9c8141e,
            0x8af93e21, 0x87f23028, 0x90ef2233, 0x9de42c3a, 0x63d96dd, 0xb3698d4, 0x1c2b8acf, 0x112084c6, 0x3211aef9,
            0x3f1aa0f0, 0x2807b2eb, 0x250cbce2, 0x6e65e695, 0x636ee89c, 0x7473fa87, 0x7978f48e, 0x5a49deb1, 0x5742d0b8,
            0x405fc2a3, 0x4d54ccaa, 0xdaf741ec, 0xd7fc4fe5, 0xc0e15dfe, 0xcdea53f7, 0xeedb79c8, 0xe3d077c1, 0xf4cd65da,
            0xf9c66bd3, 0xb2af31a4, 0xbfa43fad, 0xa8b92db6, 0xa5b223bf, 0x86830980, 0x8b880789, 0x9c951592, 0x919e1b9b,
            0xa47a17c, 0x74caf75, 0x1051bd6e, 0x1d5ab367, 0x3e6b9958, 0x33609751, 0x247d854a, 0x29768b43, 0x621fd134,
            0x6f14df3d, 0x7809cd26, 0x7502c32f, 0x5633e910, 0x5b38e719, 0x4c25f502, 0x412efb0b, 0x618c9ad7, 0x6c8794de,
            0x7b9a86c5, 0x769188cc, 0x55a0a2f3, 0x58abacfa, 0x4fb6bee1, 0x42bdb0e8, 0x9d4ea9f, 0x4dfe496, 0x13c2f68d,
            0x1ec9f884, 0x3df8d2bb, 0x30f3dcb2, 0x27eecea9, 0x2ae5c0a0, 0xb13c7a47, 0xbc37744e, 0xab2a6655, 0xa621685c,
            0x85104263, 0x881b4c6a, 0x9f065e71, 0x920d5078, 0xd9640a0f, 0xd46f0406, 0xc372161d, 0xce791814, 0xed48322b,
            0xe0433c22, 0xf75e2e39, 0xfa552030, 0xb701ec9a, 0xba0ae293, 0xad17f088, 0xa01cfe81, 0x832dd4be, 0x8e26dab7,
            0x993bc8ac, 0x9430c6a5, 0xdf599cd2, 0xd25292db, 0xc54f80c0, 0xc8448ec9, 0xeb75a4f6, 0xe67eaaff, 0xf163b8e4,
            0xfc68b6ed, 0x67b10c0a, 0x6aba0203, 0x7da71018, 0x70ac1e11, 0x539d342e, 0x5e963a27, 0x498b283c, 0x44802635,
            0xfe97c42, 0x2e2724b, 0x15ff6050, 0x18f46e59, 0x3bc54466, 0x36ce4a6f, 0x21d35874, 0x2cd8567d, 0xc7a37a1,
            0x17139a8, 0x166c2bb3, 0x1b6725ba, 0x38560f85, 0x355d018c, 0x22401397, 0x2f4b1d9e, 0x642247e9, 0x692949e0,
            0x7e345bfb, 0x733f55f2, 0x500e7fcd, 0x5d0571c4, 0x4a1863df, 0x47136dd6, 0xdccad731, 0xd1c1d938, 0xc6dccb23,
            0xcbd7c52a, 0xe8e6ef15, 0xe5ede11c, 0xf2f0f307, 0xfffbfd0e, 0xb492a779, 0xb999a970, 0xae84bb6b, 0xa38fb562,
            0x80be9f5d, 0x8db59154, 0x9aa8834f, 0x97a38d46});


    private long[] dword_5 = convertZhengLong(new long[]{0x0, 0x90d0b0e, 0x121a161c, 0x1b171d12, 0x24342c38, 0x2d392736, 0x362e3a24, 0x3f23312a, 0x48685870,
            0x4165537e, 0x5a724e6c, 0x537f4562, 0x6c5c7448, 0x65517f46, 0x7e466254, 0x774b695a, 0x90d0b0e0, 0x99ddbbee,
            0x82caa6fc, 0x8bc7adf2, 0xb4e49cd8, 0xbde997d6, 0xa6fe8ac4, 0xaff381ca, 0xd8b8e890, 0xd1b5e39e, 0xcaa2fe8c,
            0xc3aff582, 0xfc8cc4a8, 0xf581cfa6, 0xee96d2b4, 0xe79bd9ba, 0x3bbb7bdb, 0x32b670d5, 0x29a16dc7, 0x20ac66c9,
            0x1f8f57e3, 0x16825ced, 0xd9541ff, 0x4984af1, 0x73d323ab, 0x7ade28a5, 0x61c935b7, 0x68c43eb9, 0x57e70f93,
            0x5eea049d, 0x45fd198f, 0x4cf01281, 0xab6bcb3b, 0xa266c035, 0xb971dd27, 0xb07cd629, 0x8f5fe703, 0x8652ec0d,
            0x9d45f11f, 0x9448fa11, 0xe303934b, 0xea0e9845, 0xf1198557, 0xf8148e59, 0xc737bf73, 0xce3ab47d, 0xd52da96f,
            0xdc20a261, 0x766df6ad, 0x7f60fda3, 0x6477e0b1, 0x6d7aebbf, 0x5259da95, 0x5b54d19b, 0x4043cc89, 0x494ec787,
            0x3e05aedd, 0x3708a5d3, 0x2c1fb8c1, 0x2512b3cf, 0x1a3182e5, 0x133c89eb, 0x82b94f9, 0x1269ff7, 0xe6bd464d,
            0xefb04d43, 0xf4a75051, 0xfdaa5b5f, 0xc2896a75, 0xcb84617b, 0xd0937c69, 0xd99e7767, 0xaed51e3d, 0xa7d81533,
            0xbccf0821, 0xb5c2032f, 0x8ae13205, 0x83ec390b, 0x98fb2419, 0x91f62f17, 0x4dd68d76, 0x44db8678, 0x5fcc9b6a,
            0x56c19064, 0x69e2a14e, 0x60efaa40, 0x7bf8b752, 0x72f5bc5c, 0x5bed506, 0xcb3de08, 0x17a4c31a, 0x1ea9c814,
            0x218af93e, 0x2887f230, 0x3390ef22, 0x3a9de42c, 0xdd063d96, 0xd40b3698, 0xcf1c2b8a, 0xc6112084, 0xf93211ae,
            0xf03f1aa0, 0xeb2807b2, 0xe2250cbc, 0x956e65e6, 0x9c636ee8, 0x877473fa, 0x8e7978f4, 0xb15a49de, 0xb85742d0,
            0xa3405fc2, 0xaa4d54cc, 0xecdaf741, 0xe5d7fc4f, 0xfec0e15d, 0xf7cdea53, 0xc8eedb79, 0xc1e3d077, 0xdaf4cd65,
            0xd3f9c66b, 0xa4b2af31, 0xadbfa43f, 0xb6a8b92d, 0xbfa5b223, 0x80868309, 0x898b8807, 0x929c9515, 0x9b919e1b,
            0x7c0a47a1, 0x75074caf, 0x6e1051bd, 0x671d5ab3, 0x583e6b99, 0x51336097, 0x4a247d85, 0x4329768b, 0x34621fd1,
            0x3d6f14df, 0x267809cd, 0x2f7502c3, 0x105633e9, 0x195b38e7, 0x24c25f5, 0xb412efb, 0xd7618c9a, 0xde6c8794,
            0xc57b9a86, 0xcc769188, 0xf355a0a2, 0xfa58abac, 0xe14fb6be, 0xe842bdb0, 0x9f09d4ea, 0x9604dfe4, 0x8d13c2f6,
            0x841ec9f8, 0xbb3df8d2, 0xb230f3dc, 0xa927eece, 0xa02ae5c0, 0x47b13c7a, 0x4ebc3774, 0x55ab2a66, 0x5ca62168,
            0x63851042, 0x6a881b4c, 0x719f065e, 0x78920d50, 0xfd9640a, 0x6d46f04, 0x1dc37216, 0x14ce7918, 0x2bed4832,
            0x22e0433c, 0x39f75e2e, 0x30fa5520, 0x9ab701ec, 0x93ba0ae2, 0x88ad17f0, 0x81a01cfe, 0xbe832dd4, 0xb78e26da,
            0xac993bc8, 0xa59430c6, 0xd2df599c, 0xdbd25292, 0xc0c54f80, 0xc9c8448e, 0xf6eb75a4, 0xffe67eaa, 0xe4f163b8,
            0xedfc68b6, 0xa67b10c, 0x36aba02, 0x187da710, 0x1170ac1e, 0x2e539d34, 0x275e963a, 0x3c498b28, 0x35448026,
            0x420fe97c, 0x4b02e272, 0x5015ff60, 0x5918f46e, 0x663bc544, 0x6f36ce4a, 0x7421d358, 0x7d2cd856, 0xa10c7a37,
            0xa8017139, 0xb3166c2b, 0xba1b6725, 0x8538560f, 0x8c355d01, 0x97224013, 0x9e2f4b1d, 0xe9642247, 0xe0692949,
            0xfb7e345b, 0xf2733f55, 0xcd500e7f, 0xc45d0571, 0xdf4a1863, 0xd647136d, 0x31dccad7, 0x38d1c1d9, 0x23c6dccb,
            0x2acbd7c5, 0x15e8e6ef, 0x1ce5ede1, 0x7f2f0f3, 0xefffbfd, 0x79b492a7, 0x70b999a9, 0x6bae84bb, 0x62a38fb5,
            0x5d80be9f, 0x548db591, 0x4f9aa883, 0x4697a38d});

    private long[] dword_6 = convertZhengLong(new long[]{0xc66363a5, 0xf87c7c84, 0xee777799, 0xf67b7b8d, 0xfff2f20d, 0xd66b6bbd, 0xde6f6fb1, 0x91c5c554, 0x60303050,
            0x2010103, 0xce6767a9, 0x562b2b7d, 0xe7fefe19, 0xb5d7d762, 0x4dababe6, 0xec76769a, 0x8fcaca45, 0x1f82829d,
            0x89c9c940, 0xfa7d7d87, 0xeffafa15, 0xb25959eb, 0x8e4747c9, 0xfbf0f00b, 0x41adadec, 0xb3d4d467, 0x5fa2a2fd,
            0x45afafea, 0x239c9cbf, 0x53a4a4f7, 0xe4727296, 0x9bc0c05b, 0x75b7b7c2, 0xe1fdfd1c, 0x3d9393ae, 0x4c26266a,
            0x6c36365a, 0x7e3f3f41, 0xf5f7f702, 0x83cccc4f, 0x6834345c, 0x51a5a5f4, 0xd1e5e534, 0xf9f1f108, 0xe2717193,
            0xabd8d873, 0x62313153, 0x2a15153f, 0x804040c, 0x95c7c752, 0x46232365, 0x9dc3c35e, 0x30181828, 0x379696a1,
            0xa05050f, 0x2f9a9ab5, 0xe070709, 0x24121236, 0x1b80809b, 0xdfe2e23d, 0xcdebeb26, 0x4e272769, 0x7fb2b2cd,
            0xea75759f, 0x1209091b, 0x1d83839e, 0x582c2c74, 0x341a1a2e, 0x361b1b2d, 0xdc6e6eb2, 0xb45a5aee, 0x5ba0a0fb,
            0xa45252f6, 0x763b3b4d, 0xb7d6d661, 0x7db3b3ce, 0x5229297b, 0xdde3e33e, 0x5e2f2f71, 0x13848497, 0xa65353f5,
            0xb9d1d168, 0x0, 0xc1eded2c, 0x40202060, 0xe3fcfc1f, 0x79b1b1c8, 0xb65b5bed, 0xd46a6abe, 0x8dcbcb46,
            0x67bebed9, 0x7239394b, 0x944a4ade, 0x984c4cd4, 0xb05858e8, 0x85cfcf4a, 0xbbd0d06b, 0xc5efef2a, 0x4faaaae5,
            0xedfbfb16, 0x864343c5, 0x9a4d4dd7, 0x66333355, 0x11858594, 0x8a4545cf, 0xe9f9f910, 0x4020206, 0xfe7f7f81,
            0xa05050f0, 0x783c3c44, 0x259f9fba, 0x4ba8a8e3, 0xa25151f3, 0x5da3a3fe, 0x804040c0, 0x58f8f8a, 0x3f9292ad,
            0x219d9dbc, 0x70383848, 0xf1f5f504, 0x63bcbcdf, 0x77b6b6c1, 0xafdada75, 0x42212163, 0x20101030, 0xe5ffff1a,
            0xfdf3f30e, 0xbfd2d26d, 0x81cdcd4c, 0x180c0c14, 0x26131335, 0xc3ecec2f, 0xbe5f5fe1, 0x359797a2, 0x884444cc,
            0x2e171739, 0x93c4c457, 0x55a7a7f2, 0xfc7e7e82, 0x7a3d3d47, 0xc86464ac, 0xba5d5de7, 0x3219192b, 0xe6737395,
            0xc06060a0, 0x19818198, 0x9e4f4fd1, 0xa3dcdc7f, 0x44222266, 0x542a2a7e, 0x3b9090ab, 0xb888883, 0x8c4646ca,
            0xc7eeee29, 0x6bb8b8d3, 0x2814143c, 0xa7dede79, 0xbc5e5ee2, 0x160b0b1d, 0xaddbdb76, 0xdbe0e03b, 0x64323256,
            0x743a3a4e, 0x140a0a1e, 0x924949db, 0xc06060a, 0x4824246c, 0xb85c5ce4, 0x9fc2c25d, 0xbdd3d36e, 0x43acacef,
            0xc46262a6, 0x399191a8, 0x319595a4, 0xd3e4e437, 0xf279798b, 0xd5e7e732, 0x8bc8c843, 0x6e373759, 0xda6d6db7,
            0x18d8d8c, 0xb1d5d564, 0x9c4e4ed2, 0x49a9a9e0, 0xd86c6cb4, 0xac5656fa, 0xf3f4f407, 0xcfeaea25, 0xca6565af,
            0xf47a7a8e, 0x47aeaee9, 0x10080818, 0x6fbabad5, 0xf0787888, 0x4a25256f, 0x5c2e2e72, 0x381c1c24, 0x57a6a6f1,
            0x73b4b4c7, 0x97c6c651, 0xcbe8e823, 0xa1dddd7c, 0xe874749c, 0x3e1f1f21, 0x964b4bdd, 0x61bdbddc, 0xd8b8b86,
            0xf8a8a85, 0xe0707090, 0x7c3e3e42, 0x71b5b5c4, 0xcc6666aa, 0x904848d8, 0x6030305, 0xf7f6f601, 0x1c0e0e12,
            0xc26161a3, 0x6a35355f, 0xae5757f9, 0x69b9b9d0, 0x17868691, 0x99c1c158, 0x3a1d1d27, 0x279e9eb9, 0xd9e1e138,
            0xebf8f813, 0x2b9898b3, 0x22111133, 0xd26969bb, 0xa9d9d970, 0x78e8e89, 0x339494a7, 0x2d9b9bb6, 0x3c1e1e22,
            0x15878792, 0xc9e9e920, 0x87cece49, 0xaa5555ff, 0x50282878, 0xa5dfdf7a, 0x38c8c8f, 0x59a1a1f8, 0x9898980,
            0x1a0d0d17, 0x65bfbfda, 0xd7e6e631, 0x844242c6, 0xd06868b8, 0x824141c3, 0x299999b0, 0x5a2d2d77, 0x1e0f0f11,
            0x7bb0b0cb, 0xa85454fc, 0x6dbbbbd6, 0x2c16163a});

    private long[] dword_7 = convertZhengLong(new long[]{0xa5c66363, 0x84f87c7c, 0x99ee7777, 0x8df67b7b, 0xdfff2f2, 0xbdd66b6b, 0xb1de6f6f, 0x5491c5c5, 0x50603030,
            0x3020101, 0xa9ce6767, 0x7d562b2b, 0x19e7fefe, 0x62b5d7d7, 0xe64dabab, 0x9aec7676, 0x458fcaca, 0x9d1f8282,
            0x4089c9c9, 0x87fa7d7d, 0x15effafa, 0xebb25959, 0xc98e4747, 0xbfbf0f0, 0xec41adad, 0x67b3d4d4, 0xfd5fa2a2,
            0xea45afaf, 0xbf239c9c, 0xf753a4a4, 0x96e47272, 0x5b9bc0c0, 0xc275b7b7, 0x1ce1fdfd, 0xae3d9393, 0x6a4c2626,
            0x5a6c3636, 0x417e3f3f, 0x2f5f7f7, 0x4f83cccc, 0x5c683434, 0xf451a5a5, 0x34d1e5e5, 0x8f9f1f1, 0x93e27171,
            0x73abd8d8, 0x53623131, 0x3f2a1515, 0xc080404, 0x5295c7c7, 0x65462323, 0x5e9dc3c3, 0x28301818, 0xa1379696,
            0xf0a0505, 0xb52f9a9a, 0x90e0707, 0x36241212, 0x9b1b8080, 0x3ddfe2e2, 0x26cdebeb, 0x694e2727, 0xcd7fb2b2,
            0x9fea7575, 0x1b120909, 0x9e1d8383, 0x74582c2c, 0x2e341a1a, 0x2d361b1b, 0xb2dc6e6e, 0xeeb45a5a, 0xfb5ba0a0,
            0xf6a45252, 0x4d763b3b, 0x61b7d6d6, 0xce7db3b3, 0x7b522929, 0x3edde3e3, 0x715e2f2f, 0x97138484, 0xf5a65353,
            0x68b9d1d1, 0x0, 0x2cc1eded, 0x60402020, 0x1fe3fcfc, 0xc879b1b1, 0xedb65b5b, 0xbed46a6a, 0x468dcbcb,
            0xd967bebe, 0x4b723939, 0xde944a4a, 0xd4984c4c, 0xe8b05858, 0x4a85cfcf, 0x6bbbd0d0, 0x2ac5efef, 0xe54faaaa,
            0x16edfbfb, 0xc5864343, 0xd79a4d4d, 0x55663333, 0x94118585, 0xcf8a4545, 0x10e9f9f9, 0x6040202, 0x81fe7f7f,
            0xf0a05050, 0x44783c3c, 0xba259f9f, 0xe34ba8a8, 0xf3a25151, 0xfe5da3a3, 0xc0804040, 0x8a058f8f, 0xad3f9292,
            0xbc219d9d, 0x48703838, 0x4f1f5f5, 0xdf63bcbc, 0xc177b6b6, 0x75afdada, 0x63422121, 0x30201010, 0x1ae5ffff,
            0xefdf3f3, 0x6dbfd2d2, 0x4c81cdcd, 0x14180c0c, 0x35261313, 0x2fc3ecec, 0xe1be5f5f, 0xa2359797, 0xcc884444,
            0x392e1717, 0x5793c4c4, 0xf255a7a7, 0x82fc7e7e, 0x477a3d3d, 0xacc86464, 0xe7ba5d5d, 0x2b321919, 0x95e67373,
            0xa0c06060, 0x98198181, 0xd19e4f4f, 0x7fa3dcdc, 0x66442222, 0x7e542a2a, 0xab3b9090, 0x830b8888, 0xca8c4646,
            0x29c7eeee, 0xd36bb8b8, 0x3c281414, 0x79a7dede, 0xe2bc5e5e, 0x1d160b0b, 0x76addbdb, 0x3bdbe0e0, 0x56643232,
            0x4e743a3a, 0x1e140a0a, 0xdb924949, 0xa0c0606, 0x6c482424, 0xe4b85c5c, 0x5d9fc2c2, 0x6ebdd3d3, 0xef43acac,
            0xa6c46262, 0xa8399191, 0xa4319595, 0x37d3e4e4, 0x8bf27979, 0x32d5e7e7, 0x438bc8c8, 0x596e3737, 0xb7da6d6d,
            0x8c018d8d, 0x64b1d5d5, 0xd29c4e4e, 0xe049a9a9, 0xb4d86c6c, 0xfaac5656, 0x7f3f4f4, 0x25cfeaea, 0xafca6565,
            0x8ef47a7a, 0xe947aeae, 0x18100808, 0xd56fbaba, 0x88f07878, 0x6f4a2525, 0x725c2e2e, 0x24381c1c, 0xf157a6a6,
            0xc773b4b4, 0x5197c6c6, 0x23cbe8e8, 0x7ca1dddd, 0x9ce87474, 0x213e1f1f, 0xdd964b4b, 0xdc61bdbd, 0x860d8b8b,
            0x850f8a8a, 0x90e07070, 0x427c3e3e, 0xc471b5b5, 0xaacc6666, 0xd8904848, 0x5060303, 0x1f7f6f6, 0x121c0e0e,
            0xa3c26161, 0x5f6a3535, 0xf9ae5757, 0xd069b9b9, 0x91178686, 0x5899c1c1, 0x273a1d1d, 0xb9279e9e, 0x38d9e1e1,
            0x13ebf8f8, 0xb32b9898, 0x33221111, 0xbbd26969, 0x70a9d9d9, 0x89078e8e, 0xa7339494, 0xb62d9b9b, 0x223c1e1e,
            0x92158787, 0x20c9e9e9, 0x4987cece, 0xffaa5555, 0x78502828, 0x7aa5dfdf, 0x8f038c8c, 0xf859a1a1, 0x80098989,
            0x171a0d0d, 0xda65bfbf, 0x31d7e6e6, 0xc6844242, 0xb8d06868, 0xc3824141, 0xb0299999, 0x775a2d2d, 0x111e0f0f,
            0xcb7bb0b0, 0xfca85454, 0xd66dbbbb, 0x3a2c1616});

    private long[] dword_8 = convertZhengLong(new long[]{0x63a5c663, 0x7c84f87c, 0x7799ee77, 0x7b8df67b, 0xf20dfff2, 0x6bbdd66b, 0x6fb1de6f, 0xc55491c5, 0x30506030,
            0x1030201, 0x67a9ce67, 0x2b7d562b, 0xfe19e7fe, 0xd762b5d7, 0xabe64dab, 0x769aec76, 0xca458fca, 0x829d1f82,
            0xc94089c9, 0x7d87fa7d, 0xfa15effa, 0x59ebb259, 0x47c98e47, 0xf00bfbf0, 0xadec41ad, 0xd467b3d4, 0xa2fd5fa2,
            0xafea45af, 0x9cbf239c, 0xa4f753a4, 0x7296e472, 0xc05b9bc0, 0xb7c275b7, 0xfd1ce1fd, 0x93ae3d93, 0x266a4c26,
            0x365a6c36, 0x3f417e3f, 0xf702f5f7, 0xcc4f83cc, 0x345c6834, 0xa5f451a5, 0xe534d1e5, 0xf108f9f1, 0x7193e271,
            0xd873abd8, 0x31536231, 0x153f2a15, 0x40c0804, 0xc75295c7, 0x23654623, 0xc35e9dc3, 0x18283018, 0x96a13796,
            0x50f0a05, 0x9ab52f9a, 0x7090e07, 0x12362412, 0x809b1b80, 0xe23ddfe2, 0xeb26cdeb, 0x27694e27, 0xb2cd7fb2,
            0x759fea75, 0x91b1209, 0x839e1d83, 0x2c74582c, 0x1a2e341a, 0x1b2d361b, 0x6eb2dc6e, 0x5aeeb45a, 0xa0fb5ba0,
            0x52f6a452, 0x3b4d763b, 0xd661b7d6, 0xb3ce7db3, 0x297b5229, 0xe33edde3, 0x2f715e2f, 0x84971384, 0x53f5a653,
            0xd168b9d1, 0x0, 0xed2cc1ed, 0x20604020, 0xfc1fe3fc, 0xb1c879b1, 0x5bedb65b, 0x6abed46a, 0xcb468dcb,
            0xbed967be, 0x394b7239, 0x4ade944a, 0x4cd4984c, 0x58e8b058, 0xcf4a85cf, 0xd06bbbd0, 0xef2ac5ef, 0xaae54faa,
            0xfb16edfb, 0x43c58643, 0x4dd79a4d, 0x33556633, 0x85941185, 0x45cf8a45, 0xf910e9f9, 0x2060402, 0x7f81fe7f,
            0x50f0a050, 0x3c44783c, 0x9fba259f, 0xa8e34ba8, 0x51f3a251, 0xa3fe5da3, 0x40c08040, 0x8f8a058f, 0x92ad3f92,
            0x9dbc219d, 0x38487038, 0xf504f1f5, 0xbcdf63bc, 0xb6c177b6, 0xda75afda, 0x21634221, 0x10302010, 0xff1ae5ff,
            0xf30efdf3, 0xd26dbfd2, 0xcd4c81cd, 0xc14180c, 0x13352613, 0xec2fc3ec, 0x5fe1be5f, 0x97a23597, 0x44cc8844,
            0x17392e17, 0xc45793c4, 0xa7f255a7, 0x7e82fc7e, 0x3d477a3d, 0x64acc864, 0x5de7ba5d, 0x192b3219, 0x7395e673,
            0x60a0c060, 0x81981981, 0x4fd19e4f, 0xdc7fa3dc, 0x22664422, 0x2a7e542a, 0x90ab3b90, 0x88830b88, 0x46ca8c46,
            0xee29c7ee, 0xb8d36bb8, 0x143c2814, 0xde79a7de, 0x5ee2bc5e, 0xb1d160b, 0xdb76addb, 0xe03bdbe0, 0x32566432,
            0x3a4e743a, 0xa1e140a, 0x49db9249, 0x60a0c06, 0x246c4824, 0x5ce4b85c, 0xc25d9fc2, 0xd36ebdd3, 0xacef43ac,
            0x62a6c462, 0x91a83991, 0x95a43195, 0xe437d3e4, 0x798bf279, 0xe732d5e7, 0xc8438bc8, 0x37596e37, 0x6db7da6d,
            0x8d8c018d, 0xd564b1d5, 0x4ed29c4e, 0xa9e049a9, 0x6cb4d86c, 0x56faac56, 0xf407f3f4, 0xea25cfea, 0x65afca65,
            0x7a8ef47a, 0xaee947ae, 0x8181008, 0xbad56fba, 0x7888f078, 0x256f4a25, 0x2e725c2e, 0x1c24381c, 0xa6f157a6,
            0xb4c773b4, 0xc65197c6, 0xe823cbe8, 0xdd7ca1dd, 0x749ce874, 0x1f213e1f, 0x4bdd964b, 0xbddc61bd, 0x8b860d8b,
            0x8a850f8a, 0x7090e070, 0x3e427c3e, 0xb5c471b5, 0x66aacc66, 0x48d89048, 0x3050603, 0xf601f7f6, 0xe121c0e,
            0x61a3c261, 0x355f6a35, 0x57f9ae57, 0xb9d069b9, 0x86911786, 0xc15899c1, 0x1d273a1d, 0x9eb9279e, 0xe138d9e1,
            0xf813ebf8, 0x98b32b98, 0x11332211, 0x69bbd269, 0xd970a9d9, 0x8e89078e, 0x94a73394, 0x9bb62d9b, 0x1e223c1e,
            0x87921587, 0xe920c9e9, 0xce4987ce, 0x55ffaa55, 0x28785028, 0xdf7aa5df, 0x8c8f038c, 0xa1f859a1, 0x89800989,
            0xd171a0d, 0xbfda65bf, 0xe631d7e6, 0x42c68442, 0x68b8d068, 0x41c38241, 0x99b02999, 0x2d775a2d, 0xf111e0f,
            0xb0cb7bb0, 0x54fca854, 0xbbd66dbb, 0x163a2c16});

    private long[] dword_9 = convertZhengLong(new long[]{0x6363a5c6, 0x7c7c84f8, 0x777799ee, 0x7b7b8df6, 0xf2f20dff, 0x6b6bbdd6, 0x6f6fb1de, 0xc5c55491, 0x30305060,
            0x1010302, 0x6767a9ce, 0x2b2b7d56, 0xfefe19e7, 0xd7d762b5, 0xababe64d, 0x76769aec, 0xcaca458f, 0x82829d1f,
            0xc9c94089, 0x7d7d87fa, 0xfafa15ef, 0x5959ebb2, 0x4747c98e, 0xf0f00bfb, 0xadadec41, 0xd4d467b3, 0xa2a2fd5f,
            0xafafea45, 0x9c9cbf23, 0xa4a4f753, 0x727296e4, 0xc0c05b9b, 0xb7b7c275, 0xfdfd1ce1, 0x9393ae3d, 0x26266a4c,
            0x36365a6c, 0x3f3f417e, 0xf7f702f5, 0xcccc4f83, 0x34345c68, 0xa5a5f451, 0xe5e534d1, 0xf1f108f9, 0x717193e2,
            0xd8d873ab, 0x31315362, 0x15153f2a, 0x4040c08, 0xc7c75295, 0x23236546, 0xc3c35e9d, 0x18182830, 0x9696a137,
            0x5050f0a, 0x9a9ab52f, 0x707090e, 0x12123624, 0x80809b1b, 0xe2e23ddf, 0xebeb26cd, 0x2727694e, 0xb2b2cd7f,
            0x75759fea, 0x9091b12, 0x83839e1d, 0x2c2c7458, 0x1a1a2e34, 0x1b1b2d36, 0x6e6eb2dc, 0x5a5aeeb4, 0xa0a0fb5b,
            0x5252f6a4, 0x3b3b4d76, 0xd6d661b7, 0xb3b3ce7d, 0x29297b52, 0xe3e33edd, 0x2f2f715e, 0x84849713, 0x5353f5a6,
            0xd1d168b9, 0x0, 0xeded2cc1, 0x20206040, 0xfcfc1fe3, 0xb1b1c879, 0x5b5bedb6, 0x6a6abed4, 0xcbcb468d,
            0xbebed967, 0x39394b72, 0x4a4ade94, 0x4c4cd498, 0x5858e8b0, 0xcfcf4a85, 0xd0d06bbb, 0xefef2ac5, 0xaaaae54f,
            0xfbfb16ed, 0x4343c586, 0x4d4dd79a, 0x33335566, 0x85859411, 0x4545cf8a, 0xf9f910e9, 0x2020604, 0x7f7f81fe,
            0x5050f0a0, 0x3c3c4478, 0x9f9fba25, 0xa8a8e34b, 0x5151f3a2, 0xa3a3fe5d, 0x4040c080, 0x8f8f8a05, 0x9292ad3f,
            0x9d9dbc21, 0x38384870, 0xf5f504f1, 0xbcbcdf63, 0xb6b6c177, 0xdada75af, 0x21216342, 0x10103020, 0xffff1ae5,
            0xf3f30efd, 0xd2d26dbf, 0xcdcd4c81, 0xc0c1418, 0x13133526, 0xecec2fc3, 0x5f5fe1be, 0x9797a235, 0x4444cc88,
            0x1717392e, 0xc4c45793, 0xa7a7f255, 0x7e7e82fc, 0x3d3d477a, 0x6464acc8, 0x5d5de7ba, 0x19192b32, 0x737395e6,
            0x6060a0c0, 0x81819819, 0x4f4fd19e, 0xdcdc7fa3, 0x22226644, 0x2a2a7e54, 0x9090ab3b, 0x8888830b, 0x4646ca8c,
            0xeeee29c7, 0xb8b8d36b, 0x14143c28, 0xdede79a7, 0x5e5ee2bc, 0xb0b1d16, 0xdbdb76ad, 0xe0e03bdb, 0x32325664,
            0x3a3a4e74, 0xa0a1e14, 0x4949db92, 0x6060a0c, 0x24246c48, 0x5c5ce4b8, 0xc2c25d9f, 0xd3d36ebd, 0xacacef43,
            0x6262a6c4, 0x9191a839, 0x9595a431, 0xe4e437d3, 0x79798bf2, 0xe7e732d5, 0xc8c8438b, 0x3737596e, 0x6d6db7da,
            0x8d8d8c01, 0xd5d564b1, 0x4e4ed29c, 0xa9a9e049, 0x6c6cb4d8, 0x5656faac, 0xf4f407f3, 0xeaea25cf, 0x6565afca,
            0x7a7a8ef4, 0xaeaee947, 0x8081810, 0xbabad56f, 0x787888f0, 0x25256f4a, 0x2e2e725c, 0x1c1c2438, 0xa6a6f157,
            0xb4b4c773, 0xc6c65197, 0xe8e823cb, 0xdddd7ca1, 0x74749ce8, 0x1f1f213e, 0x4b4bdd96, 0xbdbddc61, 0x8b8b860d,
            0x8a8a850f, 0x707090e0, 0x3e3e427c, 0xb5b5c471, 0x6666aacc, 0x4848d890, 0x3030506, 0xf6f601f7, 0xe0e121c,
            0x6161a3c2, 0x35355f6a, 0x5757f9ae, 0xb9b9d069, 0x86869117, 0xc1c15899, 0x1d1d273a, 0x9e9eb927, 0xe1e138d9,
            0xf8f813eb, 0x9898b32b, 0x11113322, 0x6969bbd2, 0xd9d970a9, 0x8e8e8907, 0x9494a733, 0x9b9bb62d, 0x1e1e223c,
            0x87879215, 0xe9e920c9, 0xcece4987, 0x5555ffaa, 0x28287850, 0xdfdf7aa5, 0x8c8c8f03, 0xa1a1f859, 0x89898009,
            0xd0d171a, 0xbfbfda65, 0xe6e631d7, 0x4242c684, 0x6868b8d0, 0x4141c382, 0x9999b029, 0x2d2d775a, 0xf0f111e,
            0xb0b0cb7b, 0x5454fca8, 0xbbbbd66d, 0x16163a2c});

    private long[] ord_list = convertZhengLong(new long[]{0x4D, 0xD4, 0xC2, 0xE6, 0xB8, 0x31, 0x62, 0x09, 0x0E, 0x52, 0xB3, 0xC7, 0xA6, 0x73, 0x3B, 0xA4,
            0x1C, 0xB2, 0x46, 0x2B, 0x82, 0x9A, 0xB5, 0x8A, 0x19, 0x6B, 0x39, 0xDB, 0x57, 0x17, 0x75, 0x24,
            0xF4, 0x9B, 0xAF, 0x7F, 0x08, 0xE8, 0xD6, 0x8D, 0x26, 0xA7, 0x2E, 0x37, 0xC1, 0xA9, 0x5A, 0x2F,
            0x1F, 0x05, 0xA5, 0x18, 0x92, 0xAE, 0xF2, 0x94, 0x97, 0x32, 0xB6, 0x2A, 0x38, 0xAA, 0xDD, 0x58});


    private long[] hex_0A2(long[] content, long[] list_740, long[] list_55C) {
        long[] result = new long[]{};

        int l55cl = list_55C.length;

        int len = content.length;
        int end = len / 0x10;

        for (int index = 0; index < end; index++) {
            for (int j = 0; j < 0x10; j++) {
                list_740[j] = list_740[j] ^ content[0x10 * index + j];
            }
            long[] tmp_list = dump_list(list_740);
            long R6 = tmp_list[3];
            long LR = tmp_list[0];
            long R8 = tmp_list[1];
            long R12 = tmp_list[2];
            long R5 = list_55C[0];
            long R4 = list_55C[1];
            long R1 = list_55C[2];
            long R2 = list_55C[3];
            long R11 = 0;
            long v_334 = 0;
            R2 = R2 ^ R6;
            long v_33C = R2;
            R1 = R1 ^ R12;
            long v_338 = R1;
            R4 = R4 ^ R8;
            R12 = R5 ^ LR;

            for (int j = 0; j < 5; j++) {
                long R3 = v_33C;
                long R9 = R4;
                long R0 = UBFX(R12, 0x10, 8);
                R1 = R3 >> 0x18;
                R1 = dword_6[Long.valueOf(R1).intValue()];
                R0 = dword_7[Long.valueOf(R0).intValue()];
                R0 = R0 ^ R1;
                R1 = UBFX(R4, 8, 8);
                R8 = v_338;
                R1 = dword_8[Long.valueOf(R1).intValue()];
                LR = list_55C[8 * j + 6];
                R0 = R0 ^ R1;
                R1 = UTFX(R8);
                R1 = dword_9[Long.valueOf(R1).intValue()];
                R0 = R0 ^ R1;
                R1 = list_55C[8 * j + 4];
                v_334 = R1;
                R1 = list_55C[8 * j + 5];
                long v_330 = R1;
                R1 = list_55C[8 * j + 7];
                R11 = R0 ^ R1;
                R1 = UBFX(R3, 0x10, 8);
                R0 = R8 >> 24;
                R0 = dword_6[Long.valueOf(R0).intValue()];
                R1 = dword_7[Long.valueOf(R1).intValue()];
                R0 = R0 ^ R1;
                R1 = UBFX(R12, 8, 8);
                R1 = dword_8[Long.valueOf(R1).intValue()];
                R0 = R0 ^ R1;
                R1 = UTFX(R9);
                R1 = dword_9[Long.valueOf(R1).intValue()];
                R0 = R0 ^ R1;
                R1 = UBFX(R8, 0x10, 8);
                R6 = R0 ^ LR;
                R0 = R9 >> 24;
                R0 = dword_6[Long.valueOf(R0).intValue()];
                R1 = dword_7[Long.valueOf(R1).intValue()];
                R0 = R0 ^ R1;
                R1 = UBFX(R3, 8, 8);
                R1 = dword_8[Long.valueOf(R1).intValue()];
                R0 = R0 ^ R1;
                R1 = UTFX(R12);
                R1 = dword_9[Long.valueOf(R1).intValue()];
                R0 = R0 ^ R1;
                R1 = v_330;
                LR = R0 ^ R1;
                R0 = UTFX(R3);
                R0 = dword_9[Long.valueOf(R0).intValue()];
                R4 = R12 >> 24;
                R1 = UBFX(R8, 8, 8);
                R4 = dword_6[Long.valueOf(R4).intValue()];
                R5 = UBFX(R9, 0x10, 8);
                R1 = dword_8[Long.valueOf(R1).intValue()];
                R5 = dword_7[Long.valueOf(R5).intValue()];
                R5 = R5 ^ R4;
                R1 = R1 ^ R5;
                R0 = R0 ^ R1;
                R1 = v_334;
                R1 = R1 ^ R0;
                R0 = R1 >> 0x18;
                v_334 = R0;
                if (j == 4) {
                    break;
                } else {
                    R4 = UBFX(R1, 0x10, 8);
                    R5 = R11 >> 24;
                    long R10 = R6;
                    R5 = dword_6[Long.valueOf(R5).intValue()];
                    R4 = dword_7[Long.valueOf(R4).intValue()];
                    R5 = R5 ^ R4;
                    R4 = UBFX(LR, 8, 8);
                    R4 = dword_8[Long.valueOf(R4).intValue()];
                    R5 = R5 ^ R4;
                    R4 = UTFX(R10);
                    R4 = dword_9[Long.valueOf(R4).intValue()];
                    R5 = R5 ^ R4;
                    R4 = list_55C[8 * j + 11];
                    R0 = R5 ^ R4;
                    v_33C = R0;
                    R4 = UBFX(R11, 0x10, 8);
                    R5 = R10 >> 24;
                    R5 = dword_6[Long.valueOf(R5).intValue()];
                    R4 = dword_7[Long.valueOf(R4).intValue()];
                    R5 = R5 ^ R4;
                    R4 = UBFX(R1, 8, 8);
                    R0 = list_55C[8 * j + 9];
                    R9 = list_55C[8 * j + 8];
                    R1 = UTFX(R1);
                    R4 = dword_8[Long.valueOf(R4).intValue()];
                    R1 = dword_9[Long.valueOf(R1).intValue()];
                    R5 = R5 ^ R4;
                    R4 = UTFX(LR);
                    R4 = dword_9[Long.valueOf(R4).intValue()];
                    R5 = R5 ^ R4;
                    R4 = list_55C[8 * j + 10];
                    R4 = R4 ^ R5;
                    v_338 = R4;
                    R5 = UBFX(R10, 0x10, 8);
                    R4 = LR >> 24;
                    R4 = dword_6[Long.valueOf(R4).intValue()];
                    R5 = dword_7[Long.valueOf(R5).intValue()];
                    R4 = R4 ^ R5;
                    R5 = UBFX(R11, 8, 8);
                    R5 = dword_8[Long.valueOf(R5).intValue()];
                    R4 = R4 ^ R5;
                    R1 = R1 ^ R4;
                    R4 = R1 ^ R0;
                    R0 = v_334;
                    R1 = UBFX(LR, 0x10, 8);
                    R5 = UBFX(R10, 8, 8);
                    R0 = dword_6[Long.valueOf(R0).intValue()];
                    R1 = dword_7[Long.valueOf(R1).intValue()];
                    R5 = dword_8[Long.valueOf(R5).intValue()];
                    R0 = R0 ^ R1;
                    R1 = UTFX(R11);
                    R1 = dword_9[Long.valueOf(R1).intValue()];
                    R0 = R0 ^ R5;
                    R0 = R0 ^ R1;
                    R12 = R0 ^ R9;
                }
            }
            R2 = R11 >> 24;
            long R3 = UBFX(R1, 0x10, 8);
            long R10 = R6;
            long R0 = R10 >> 24;
            R2 = dword_0[Long.valueOf(R2).intValue()];
            R2 = Long.parseLong(Long.toHexString(R2) + "000000", 16);
            long R9 = R10;
            R3 = dword_0[Long.valueOf(R3).intValue()];
            R3 = Long.parseLong(Long.toHexString(R3) + "0000", 16);
            R0 = dword_0[Long.valueOf(R0).intValue()];
            R0 = Long.parseLong(Long.toHexString(R0) + "000000", 16);
            R2 = R2 ^ R3;
            long v_350 = R2;
            R2 = UBFX(R11, 0x10, 8);
            R2 = dword_0[Long.valueOf(R2).intValue()];
            R2 = Long.parseLong(Long.toHexString(R2) + "0000", 16);
            R0 = R0 ^ R2;
            R2 = UBFX(R1, 8, 8);
            R1 = UTFX(R1);
            R2 = dword_0[Long.valueOf(R2).intValue()];
            R2 = Long.parseLong(Long.toHexString(R2) + "00", 16);
            R1 = dword_0[Long.valueOf(R1).intValue()];
            R0 = R0 ^ R2;
            R2 = UTFX(LR);
            R2 = dword_0[Long.valueOf(R2).intValue()];
            R12 = R0 ^ R2;
            R0 = list_55C[l55cl - 2];
            R10 = list_55C[l55cl - 3];
            R12 = R12 ^ R0;
            R2 = list_55C[l55cl - 1];
            R0 = LR >> 24;
            long v_34C = R2;
            R2 = UBFX(R9, 0x10, 8);
            R0 = dword_0[Long.valueOf(R0).intValue()];
            R0 = Long.parseLong(Long.toHexString(R0) + "000000", 16);
            R2 = dword_0[Long.valueOf(R2).intValue()];
            R2 = Long.parseLong(Long.toHexString(R2) + "0000", 16);
            R0 = R0 ^ R2;
            R2 = UBFX(R11, 8, 8);
            R2 = dword_0[Long.valueOf(R2).intValue()];
            R2 = Long.parseLong(Long.toHexString(R2) + "00", 16);
            R0 = R0 ^ R2;
            R0 = R0 ^ R1;
            R1 = R0 ^ R10;
            R0 = v_334;
            R2 = UBFX(LR, 0x10, 8);
            R0 = dword_0[Long.valueOf(R0).intValue()];
            R0 = Long.parseLong(Long.toHexString(R0) + "000000", 16);
            R2 = dword_0[Long.valueOf(R2).intValue()];
            R2 = Long.parseLong(Long.toHexString(R2) + "0000", 16);
            R0 = R0 ^ R2;
            R2 = UBFX(R9, 8, 8);
            R2 = dword_0[Long.valueOf(R2).intValue()];
            R2 = Long.parseLong(Long.toHexString(R2) + "00", 16);
            R0 = R0 ^ R2;
            R2 = UTFX(R11);
            R2 = dword_0[Long.valueOf(R2).intValue()];
            R0 = R0 ^ R2;
            R2 = UTFX(R9);
            R2 = dword_0[Long.valueOf(R2).intValue()];
            R3 = UBFX(LR, 8, 8);
            R3 = dword_0[Long.valueOf(R3).intValue()];
            R3 = Long.parseLong(Long.toHexString(R3) + "00", 16);
            R5 = v_350;
            R6 = list_55C[l55cl - 4];
            R3 = R3 ^ R5;
            R2 = R2 ^ R3;
            R3 = v_34C;
            R0 = R0 ^ R6;
            R2 = R2 ^ R3;
            //R3 = R0 >> 0x10
            list_740 = hex_list(new long[]{R0, R1, R12, R2});
            result = ArrayUtils.addAll(result, list_740);
        }
        return result;
    }

    public long[] hex_9C8() {
        List<Long> result = new ArrayList<>();
        for (int index = 0; index < 0x20; index++) {
            //result.add(  1L * choice( 0 ,0x100) );
            result.add(0L);
        }
        long[] array = result.stream().mapToLong(Long::valueOf).toArray();
        return array;
    }

    public long[] hex_27E(long[] param_list) {
        long R6 = param_list[0];
        long R8 = param_list[1];
        for (int i = 0; i < 0x40; i++) {
            long R0 = param_list[2 * i + 0x1c];
            long R5 = param_list[2 * i + 0x1d];
            long R4 = LSRS(R0, 0x13);
            long R3 = LSRS(R0, 0x1d);
            long LR = R4 | check(R5) << 13;
            R4 = LSLS(R0, 3);
            R4 = R4 | check(R5) >> 29;
            R3 = R3 | check(R5) << 3;
            R4 = R4 ^ check(R0) >> 6;
            LR = LR ^ R4;
            R4 = LSRS(R5, 6);
            R4 = R4 | check(R0) << 26;
            long R9 = R3 ^ R4;
            R4 = LSRS(R5, 0x13);
            R0 = R4 | check(R0) << 13;
            long R10 = param_list[2 * i + 0x12];
            R3 = param_list[2 * i + 0x13];
            R5 = param_list[2 * i + 0x2];
            R4 = param_list[2 * i + 0x3];
            R0 = R0 ^ R9;
            R3 = ADDS(R3, R8);
            R6 = ADC(R6, R10);
            R8 = ADDS(R3, R0);
            LR = ADC(LR, R6);
            R6 = LSRS(R4, 7);
            R3 = LSRS(R4, 8);
            R6 = R6 | check(R5) << 25;
            R3 = R3 | check(R5) << 24;
            R3 = EORS(R3, R6);
            R6 = LSRS(R5, 1);
            R0 = RRX(R4);
            R0 = EORS(R0, R3);
            R3 = R6 | check(R4) << 31;
            R6 = LSRS(R5, 8);
            R0 = ADDS(R0, R8);
            R6 = R6 | check(R4) << 24;
            R8 = R4;
            R6 = R6 ^ check(R5) >> 7;
            R3 = R3 ^ R6;
            R6 = R5;
            R3 = ADC(R3, LR);
            param_list = ArrayUtils.addAll(param_list, new long[]{R3, R0});
        }
        return param_list;
    }


    public long[] hex_30A(long[] param_list, long[] list_3B8) {
        long v_3A0 = param_list[7];
        long v_3A4 = param_list[6];
        long v_374 = param_list[5];
        long v_378 = param_list[4];
        long LR = param_list[0];
        long R12 = param_list[1];
        long v_39C = param_list[2];
        long v_398 = param_list[3];
        long v_3AC = param_list[11];
        long v_3A8 = param_list[10];
        long R9 = param_list[12];
        long R10 = param_list[13];
        long R5 = param_list[9];
        long R8 = param_list[8];
        long R4 = param_list[15];
        long R6 = param_list[14];
        for (int index = 0; index < 0xA; index++) {
            long v_384 = R5;
            long R3 = rodata[0x10 * index];
            long R1 = rodata[0x10 * index + 2];
            long R2 = rodata[0x10 * index + 1];
            R3 = ADDS(R3, R6);
            R6 = check(R8) >> 14;
            long v_390 = R1;
            R6 = R6 | check(R5) << 18;
            R1 = rodata[0x10 * index + 3];
            long R0 = rodata[0x10 * index + 4];
            long v_36C = R0;
            R0 = ADC(R2, R4);
            R2 = LSRS(R5, 0x12);
            R4 = LSRS(R5, 0xE);
            R2 = R2 | check(R8) << 14;
            R4 = R4 | check(R8) << 18;
            R2 = EORS(R2, R4);
            R4 = LSLS(R5, 0x17);
            R4 = R4 | check(R8) >> 9;
            long v_38C = R1;
            R2 = EORS(R2, R4);
            R4 = check(R8) >> 18;
            R4 = R4 | check(R5) << 14;
            R6 = EORS(R6, R4);
            R4 = LSRS(R5, 9);
            R4 = R4 | check(R8) << 23;
            long v_354 = R8;
            R6 = EORS(R6, R4);
            R3 = ADDS(R3, R6);
            R0 = ADCS(R0, R2);
            R2 = list_3B8[0x10 * index + 1];
            R2 = ADDS(R2, R3);
            R3 = list_3B8[0x10 * index + 3];
            R6 = list_3B8[0x10 * index];
            long v_358 = R10;
            R6 = ADCS(R6, R0);
            R0 = v_3AC;
            long v_360 = R3;
            R0 = R0 ^ R10;
            R3 = list_3B8[0x10 * index + 2];
            R0 = ANDS(R0, R5);
            R1 = list_3B8[0x10 * index + 5];
            R4 = R0 ^ R10;
            R0 = v_3A8;
            long v_364 = R1;
            R0 = R0 ^ R9;
            R1 = v_374;
            R0 = R0 & R8;
            R8 = v_39C;
            R0 = R0 ^ R9;
            long v_35C = R3;
            R10 = ADDS(R2, R0);
            R0 = v_398;
            long R11 = ADC(R6, R4);
            R3 = v_378;
            R2 = R0 | R12;
            R6 = R0 & R12;
            R2 = ANDS(R2, R1);
            R1 = R0;
            R2 = ORRS(R2, R6);
            R6 = R8 | LR;
            R6 = ANDS(R6, R3);
            R3 = R8 & LR;
            R3 = ORRS(R3, R6);
            R6 = check(R12) << 30;
            R0 = check(R12) >> 28;
            R6 = R6 | check(LR) >> 2;
            R0 = R0 | check(LR) << 4;
            R4 = check(LR) >> 28;
            R0 = EORS(R0, R6);
            R6 = check(R12) << 25;
            R6 = R6 | check(LR) >> 7;
            R4 = R4 | check(R12) << 4;
            R0 = EORS(R0, R6);
            R6 = check(R12) >> 2;
            R6 = R6 | check(LR) << 30;
            R3 = ADDS(R3, R10);
            R6 = R6 ^ R4;
            R4 = check(R12) >> 7;
            R4 = R4 | check(LR) << 25;
            R2 = ADC(R2, R11);
            R6 = EORS(R6, R4);
            long v_37C = R12;
            R5 = ADDS(R3, R6);
            R6 = ADC(R2, R0);
            R0 = R6 | R12;
            R2 = R6 & R12;
            R0 = ANDS(R0, R1);
            R3 = LSRS(R6, 0x1C);
            R0 = ORRS(R0, R2);
            R2 = LSLS(R6, 0x1E);
            R2 = R2 | check(R5) >> 2;
            R3 = R3 | check(R5) << 4;
            R2 = EORS(R2, R3);
            R3 = LSLS(R6, 0x19);
            R3 = R3 | check(R5) >> 7;
            R4 = LSRS(R5, 0x1C);
            R3 = EORS(R3, R2);
            R2 = LSRS(R6, 2);
            R2 = R2 | check(R5) << 30;
            R4 = R4 | check(R6) << 4;
            R2 = EORS(R2, R4);
            R4 = LSRS(R6, 7);
            R4 = R4 | check(R5) << 25;
            R12 = R6;
            R2 = EORS(R2, R4);
            R4 = R5 | LR;
            R4 = R4 & R8;
            R6 = R5 & LR;
            R4 = ORRS(R4, R6);
            long v_388 = R5;
            R5 = ADDS(R2, R4);
            R0 = ADCS(R0, R3);
            v_398 = R1;
            R4 = R9;
            long v_350 = R0;
            R0 = v_3A4;
            R1 = v_3A0;
            long v_380 = LR;
            LR = ADDS(R0, R10);
            R9 = ADC(R1, R11);
            R0 = v_3AC;
            R6 = check(LR) >> 14;
            R1 = v_384;
            R3 = check(R9) >> 18;
            R2 = check(R9) >> 14;
            R3 = R3 | check(LR) << 14;
            R2 = R2 | check(LR) << 18;
            R2 = EORS(R2, R3);
            R3 = check(R9) << 23;
            R3 = R3 | check(LR) >> 9;
            R6 = R6 | check(R9) << 18;
            R2 = EORS(R2, R3);
            R3 = check(LR) >> 18;
            R3 = R3 | check(R9) << 14;
            v_39C = R8;
            R3 = EORS(R3, R6);
            R6 = check(R9) >> 9;
            R6 = R6 | check(LR) << 23;
            R8 = v_354;
            R3 = EORS(R3, R6);
            R6 = R0 ^ R1;
            R6 = R6 & R9;
            long v_370 = R12;
            R6 = EORS(R6, R0);
            R0 = v_3A8;
            R1 = R0 ^ R8;
            R1 = R1 & LR;
            R1 = EORS(R1, R0);
            R0 = v_358;
            R1 = ADDS(R1, R4);
            R6 = ADCS(R6, R0);
            R0 = v_390;
            R1 = ADDS(R1, R0);
            R0 = v_38C;
            R6 = ADCS(R6, R0);
            R0 = v_360;
            R1 = ADDS(R1, R0);
            R0 = v_35C;
            R6 = ADCS(R6, R0);
            R1 = ADDS(R1, R3);
            R3 = ADC(R6, R2);
            R2 = v_350;
            R0 = ADDS(R5, R1);
            R5 = v_37C;
            R4 = ADC(R2, R3);
            v_390 = R4;
            R2 = R4 | R12;
            R6 = R4 & R12;
            R2 = ANDS(R2, R5);
            R5 = LSRS(R4, 0x1C);
            R10 = R2 | R6;
            R2 = LSLS(R4, 0x1E);
            R2 = R2 | check(R0) >> 2;
            R5 = R5 | check(R0) << 4;
            R2 = EORS(R2, R5);
            R5 = LSLS(R4, 0x19);
            R5 = R5 | check(R0) >> 7;
            R6 = LSRS(R0, 0x1C);
            R12 = R2 ^ R5;
            R2 = LSRS(R4, 2);
            R2 = R2 | check(R0) << 30;
            R6 = R6 | check(R4) << 4;
            R2 = EORS(R2, R6);
            R6 = LSRS(R4, 7);
            R4 = v_388;
            R6 = R6 | check(R0) << 25;
            R5 = v_380;
            R2 = EORS(R2, R6);
            R6 = R0 | R4;
            R4 = ANDS(R4, R0);
            R6 = ANDS(R6, R5);
            v_38C = R0;
            R4 = ORRS(R4, R6);
            R6 = LR ^ R8;
            R0 = ADDS(R2, R4);
            v_3A4 = R0;
            R0 = ADC(R12, R10);
            v_3A0 = R0;
            R0 = v_378;
            R10 = ADDS(R1, R0);
            R0 = v_374;
            R6 = R6 & R10;
            R1 = ADC(R3, R0);
            R5 = check(R10) >> 14;
            R0 = v_384;
            R6 = R6 ^ R8;
            R3 = LSRS(R1, 0x12);
            R4 = LSRS(R1, 0xE);
            R3 = R3 | check(R10) << 14;
            R4 = R4 | check(R10) << 18;
            R3 = EORS(R3, R4);
            R4 = LSLS(R1, 0x17);
            R4 = R4 | check(R10) >> 9;
            R5 = R5 | check(R1) << 18;
            R11 = R3 ^ R4;
            R3 = check(R10) >> 18;
            R3 = R3 | check(R1) << 14;
            v_378 = R1;
            R3 = EORS(R3, R5);
            R5 = LSRS(R1, 9);
            R5 = R5 | check(R10) << 23;
            R3 = EORS(R3, R5);
            R5 = R9 ^ R0;
            R5 = ANDS(R5, R1);
            R1 = v_3A8;
            R5 = EORS(R5, R0);
            R0 = v_36C;
            R4 = ADDS(R0, R1);
            R2 = rodata[0x10 * index + 5];
            R0 = v_3AC;
            R2 = ADCS(R2, R0);
            R0 = v_364;
            R4 = ADDS(R4, R0);
            R12 = list_3B8[0x10 * index + 4];
            R0 = v_3A4;
            R2 = ADC(R2, R12);
            R6 = ADDS(R6, R4);
            R2 = ADCS(R2, R5);
            R3 = ADDS(R3, R6);
            R11 = ADC(R11, R2);
            R1 = ADDS(R0, R3);
            R0 = v_3A0;
            R6 = v_390;
            R4 = check(R1) >> 28;
            R0 = ADC(R0, R11);
            R5 = v_370;
            R2 = R0 | R6;
            R6 = ANDS(R6, R0);
            R2 = ANDS(R2, R5);
            R5 = LSRS(R0, 0x1C);
            R12 = R2 | R6;
            R6 = LSLS(R0, 0x1E);
            R6 = R6 | check(R1) >> 2;
            R5 = R5 | check(R1) << 4;
            R6 = EORS(R6, R5);
            R5 = LSLS(R0, 0x19);
            R5 = R5 | check(R1) >> 7;
            R4 = R4 | check(R0) << 4;
            R6 = EORS(R6, R5);
            R5 = LSRS(R0, 2);
            R5 = R5 | check(R1) << 30;
            v_3AC = R0;
            R5 = EORS(R5, R4);
            R4 = LSRS(R0, 7);
            R0 = v_38C;
            R4 = R4 | check(R1) << 25;
            R2 = v_388;
            R5 = EORS(R5, R4);
            R4 = R1 | R0;
            v_3A8 = R1;
            R4 = ANDS(R4, R2);
            R2 = R1 & R0;
            R2 = ORRS(R2, R4);
            R0 = ADDS(R5, R2);
            v_3A4 = R0;
            R0 = ADC(R6, R12);
            v_3A0 = R0;
            R0 = v_39C;
            R2 = v_398;
            R0 = ADDS(R0, R3);
            v_39C = R0;
            R11 = ADC(R11, R2);
            R4 = LSRS(R0, 0xE);
            R3 = check(R11) >> 18;
            R6 = check(R11) >> 14;
            R3 = R3 | check(R0) << 14;
            R6 = R6 | check(R0) << 18;
            R3 = EORS(R3, R6);
            R6 = check(R11) << 23;
            R6 = R6 | check(R0) >> 9;
            R4 = R4 | check(R11) << 18;
            R1 = EORS(R3, R6);
            R6 = LSRS(R0, 0x12);
            R6 = R6 | check(R11) << 14;
            R3 = R10 ^ LR;
            R6 = EORS(R6, R4);
            R4 = check(R11) >> 9;
            R3 = ANDS(R3, R0);
            R4 = R4 | check(R0) << 23;
            R5 = R6 ^ R4;
            v_398 = R1;
            R3 = R3 ^ LR;
            R1 = v_378;
            R6 = rodata[0x10 * index + 6];
            R12 = rodata[0x10 * index + 7];
            R4 = R1 ^ R9;
            R0 = v_384;
            R6 = ADDS(R6, R8);
            R4 = R4 & R11;
            R12 = ADC(R12, R0);
            R4 = R4 ^ R9;
            R8 = list_3B8[0x10 * index + 7];
            R2 = list_3B8[0x10 * index + 6];
            R6 = ADDS(R6, R8);
            R0 = v_398;
            R2 = ADC(R2, R12);
            R3 = ADDS(R3, R6);
            R2 = ADCS(R2, R4);
            R6 = ADDS(R3, R5);
            R12 = ADC(R2, R0);
            R0 = v_3A4;
            R4 = v_390;
            R1 = ADDS(R0, R6);
            R0 = v_3A0;
            v_384 = R1;
            R5 = ADC(R0, R12);
            R0 = v_3AC;
            R8 = check(R1) >> 28;
            R2 = R5 | R0;
            R3 = R8 | check(R5) << 4;
            R2 = ANDS(R2, R4);
            R4 = R5 & R0;
            R0 = R2 | R4;
            R4 = LSLS(R5, 0x1E);
            R2 = LSRS(R5, 0x1C);
            R4 = R4 | check(R1) >> 2;
            R2 = R2 | check(R1) << 4;
            v_3A0 = R0;
            R2 = EORS(R2, R4);
            R4 = LSLS(R5, 0x19);
            R4 = R4 | check(R1) >> 7;
            R0 = v_3A8;
            R2 = EORS(R2, R4);
            R4 = LSRS(R5, 2);
            R4 = R4 | check(R1) << 30;
            R8 = R5;
            R3 = EORS(R3, R4);
            R4 = LSRS(R5, 7);
            R4 = R4 | check(R1) << 25;
            R5 = v_38C;
            R3 = EORS(R3, R4);
            R4 = R1 | R0;
            R4 = ANDS(R4, R5);
            R5 = R1 & R0;
            R4 = ORRS(R4, R5);
            v_36C = R8;
            R0 = ADDS(R3, R4);
            v_3A4 = R0;
            R0 = v_3A0;
            R0 = ADCS(R0, R2);
            v_3A0 = R0;
            R0 = v_380;
            R2 = v_37C;
            R0 = ADDS(R0, R6);
            R5 = ADC(R12, R2);
            v_37C = R5;
            R4 = LSRS(R0, 0xE);
            v_380 = R0;
            R2 = LSRS(R5, 0x12);
            R3 = LSRS(R5, 0xE);
            R2 = R2 | check(R0) << 14;
            R3 = R3 | check(R0) << 18;
            R2 = EORS(R2, R3);
            R3 = LSLS(R5, 0x17);
            R3 = R3 | check(R0) >> 9;
            R4 = R4 | check(R5) << 18;
            R1 = R2 ^ R3;
            R3 = LSRS(R0, 0x12);
            R3 = R3 | check(R5) << 14;
            v_398 = R1;
            R3 = EORS(R3, R4);
            R4 = LSRS(R5, 9);
            R1 = v_378;
            R4 = R4 | check(R0) << 23;
            R12 = R3 ^ R4;
            R3 = list_3B8[0x10 * index + 9];
            R4 = R11 ^ R1;
            R4 = ANDS(R4, R5);
            R4 = EORS(R4, R1);
            R1 = v_39C;
            R5 = R1 ^ R10;
            R5 = ANDS(R5, R0);
            R5 = R5 ^ R10;
            R2 = rodata[0x10 * index + 8];
            R0 = ADDS(R2, LR);
            R2 = rodata[0x10 * index + 9];
            R2 = ADC(R2, R9);
            R0 = ADDS(R0, R3);
            R3 = list_3B8[0x10 * index + 8];
            R2 = ADCS(R2, R3);
            R0 = ADDS(R0, R5);
            R2 = ADCS(R2, R4);
            R1 = ADDS(R0, R12);
            R0 = v_398;
            R3 = v_3AC;
            R4 = ADC(R2, R0);
            R0 = v_3A4;
            R6 = ADDS(R0, R1);
            R0 = v_3A0;
            v_3A4 = R6;
            R0 = ADCS(R0, R4);
            v_3A0 = R0;
            R2 = R0 | R8;
            R2 = ANDS(R2, R3);
            R3 = R0 & R8;
            LR = R2 | R3;
            R8 = R6;
            R3 = LSLS(R0, 0x1E);
            R5 = LSRS(R0, 0x1C);
            R3 = R3 | check(R8) >> 2;
            R5 = R5 | check(R8) << 4;
            R3 = EORS(R3, R5);
            R5 = LSLS(R0, 0x19);
            R5 = R5 | check(R8) >> 7;
            R2 = check(R8) >> 28;
            R12 = R3 ^ R5;
            R5 = LSRS(R0, 2);
            R5 = R5 | check(R8) << 30;
            R2 = R2 | check(R0) << 4;
            R2 = EORS(R2, R5);
            R5 = LSRS(R0, 7);
            R3 = v_384;
            R5 = R5 | check(R8) << 25;
            R6 = v_3A8;
            R2 = EORS(R2, R5);
            R5 = R8 | R3;
            R5 = ANDS(R5, R6);
            R6 = R8 & R3;
            R5 = ORRS(R5, R6);
            R0 = ADDS(R2, R5);
            v_398 = R0;
            R2 = v_388;
            R12 = ADC(R12, LR);
            R0 = v_370;
            R3 = ADDS(R1, R2);
            R1 = v_380;
            R8 = ADC(R4, R0);
            R0 = R3;
            R2 = check(R8) >> 18;
            R3 = check(R8) >> 14;
            R2 = R2 | check(R0) << 14;
            R3 = R3 | check(R0) << 18;
            R2 = EORS(R2, R3);
            R3 = check(R8) << 23;
            R3 = R3 | check(R0) >> 9;
            R4 = LSRS(R0, 0xE);
            LR = R2 ^ R3;
            R3 = LSRS(R0, 0x12);
            R3 = R3 | check(R8) << 14;
            R4 = R4 | check(R8) << 18;
            R3 = EORS(R3, R4);
            R4 = check(R8) >> 9;
            R4 = R4 | check(R0) << 23;
            R2 = R0;
            R0 = v_37C;
            R3 = EORS(R3, R4);
            v_388 = R2;
            R4 = R0 ^ R11;
            R0 = v_39C;
            R4 = R4 & R8;
            R5 = R1 ^ R0;
            R4 = R4 ^ R11;
            R5 = ANDS(R5, R2);
            R5 = EORS(R5, R0);
            R6 = rodata[0x10 * index + 10];
            R1 = ADDS(R6, R10);
            R6 = rodata[0x10 * index + 11];
            R0 = v_378;
            R6 = ADCS(R6, R0);
            R2 = list_3B8[0x10 * index + 11];
            R1 = ADDS(R1, R2);
            R2 = list_3B8[0x10 * index + 10];
            R0 = v_398;
            R2 = ADCS(R2, R6);
            R1 = ADDS(R1, R5);
            R2 = ADCS(R2, R4);
            R1 = ADDS(R1, R3);
            R4 = ADC(R2, LR);
            R6 = v_3A0;
            R0 = ADDS(R0, R1);
            R9 = ADC(R12, R4);
            R3 = v_36C;
            R2 = R9 | R6;
            R5 = check(R9) >> 28;
            v_374 = R9;
            R2 = ANDS(R2, R3);
            R3 = R9 & R6;
            R10 = R2 | R3;
            R3 = check(R9) << 30;
            R3 = R3 | check(R0) >> 2;
            R5 = R5 | check(R0) << 4;
            R3 = EORS(R3, R5);
            R5 = check(R9) << 25;
            R5 = R5 | check(R0) >> 7;
            R6 = LSRS(R0, 0x1C);
            R12 = R3 ^ R5;
            R5 = check(R9) >> 2;
            R5 = R5 | check(R0) << 30;
            R6 = R6 | check(R9) << 4;
            R5 = EORS(R5, R6);
            R6 = check(R9) >> 7;
            R3 = v_3A4;
            R6 = R6 | check(R0) << 25;
            R2 = v_384;
            R5 = EORS(R5, R6);
            R6 = R0 | R3;
            R6 = ANDS(R6, R2);
            R2 = R0 & R3;
            R2 = R2 | R6;
            R2 = ADDS(R2, R5);
            v_398 = R2;
            R2 = ADC(R12, R10);
            v_378 = R2;
            R2 = v_38C;
            R12 = ADDS(R1, R2);
            R1 = v_390;
            LR = ADC(R4, R1);
            R4 = check(R12) >> 14;
            R1 = check(LR) >> 18;
            R2 = check(LR) >> 14;
            R1 = R1 | check(R12) << 14;
            R2 = R2 | check(R12) << 18;
            R1 = EORS(R1, R2);
            R2 = check(LR) << 23;
            R2 = R2 | check(R12) >> 9;
            R4 = R4 | check(LR) << 18;
            R1 = EORS(R1, R2);
            R2 = check(R12) >> 18;
            R2 = R2 | check(LR) << 14;
            v_390 = R1;
            R2 = EORS(R2, R4);
            R4 = check(LR) >> 9;
            R1 = v_37C;
            R4 = R4 | check(R12) << 23;
            R10 = R2 ^ R4;
            R2 = v_388;
            R4 = R8 ^ R1;
            R4 = R4 & LR;
            R4 = EORS(R4, R1);
            R1 = v_380;
            R5 = R2 ^ R1;
            R2 = v_39C;
            R5 = R5 & R12;
            R5 = EORS(R5, R1);
            R6 = rodata[0x10 * index + 12];
            R3 = rodata[0x10 * index + 13];
            R6 = ADDS(R6, R2);
            R3 = ADC(R3, R11);
            R1 = list_3B8[0x10 * index + 13];
            R1 = ADDS(R1, R6);
            R6 = list_3B8[0x10 * index + 12];
            R3 = ADCS(R3, R6);
            R1 = ADDS(R1, R5);
            R3 = ADCS(R3, R4);
            R5 = ADDS(R1, R10);
            R1 = v_390;
            R2 = ADC(R3, R1);
            R1 = v_398;
            R3 = v_3A0;
            R10 = ADDS(R1, R5);
            R1 = v_378;
            v_378 = R0;
            R11 = ADC(R1, R2);
            R6 = check(R10) >> 28;
            R1 = R11 | R9;
            v_398 = R11;
            R1 = ANDS(R1, R3);
            R3 = R11 & R9;
            R9 = R1 | R3;
            R3 = check(R11) << 30;
            R4 = check(R11) >> 28;
            R3 = R3 | check(R10) >> 2;
            R4 = R4 | check(R10) << 4;
            R6 = R6 | check(R11) << 4;
            R3 = EORS(R3, R4);
            R4 = check(R11) << 25;
            R4 = R4 | check(R10) >> 7;
            R1 = v_3A4;
            R3 = EORS(R3, R4);
            R4 = check(R11) >> 2;
            R4 = R4 | check(R10) << 30;
            v_39C = R10;
            R4 = EORS(R4, R6);
            R6 = check(R11) >> 7;
            R6 = R6 | check(R10) << 25;
            R4 = EORS(R4, R6);
            R6 = R10 | R0;
            R6 = ANDS(R6, R1);
            R1 = R10 & R0;
            R1 = ORRS(R1, R6);
            R10 = LR;
            R0 = ADDS(R4, R1);
            v_390 = R0;
            R0 = ADC(R3, R9);
            v_38C = R0;
            R0 = v_3A8;
            R9 = R12;
            R4 = ADDS(R5, R0);
            R0 = v_3AC;
            v_3A8 = R4;
            R0 = ADCS(R0, R2);
            R3 = LSRS(R4, 0xE);
            v_3AC = R0;
            R1 = LSRS(R0, 0x12);
            R2 = LSRS(R0, 0xE);
            R1 = R1 | check(R4) << 14;
            R2 = R2 | check(R4) << 18;
            R1 = EORS(R1, R2);
            R2 = LSLS(R0, 0x17);
            R2 = R2 | check(R4) >> 9;
            R3 = R3 | check(R0) << 18;
            R11 = R1 ^ R2;
            R2 = LSRS(R4, 0x12);
            R2 = R2 | check(R0) << 14;
            R2 = EORS(R2, R3);
            R3 = LSRS(R0, 9);
            R3 = R3 | check(R4) << 23;
            R2 = EORS(R2, R3);
            R3 = LR ^ R8;
            R3 = ANDS(R3, R0);
            R0 = v_388;
            LR = R3 ^ R8;
            R5 = R12 ^ R0;
            R5 = ANDS(R5, R4);
            R3 = R0;
            R5 = EORS(R5, R0);
            R4 = rodata[0x10 * index + 14];
            R6 = rodata[0x10 * index + 15];
            R0 = v_380;
            R4 = ADDS(R4, R0);
            R0 = v_37C;
            R6 = ADCS(R6, R0);
            R0 = list_3B8[0x10 * index + 14];
            R1 = list_3B8[0x10 * index + 15];
            R1 = ADDS(R1, R4);
            R0 = ADCS(R0, R6);
            R1 = ADDS(R1, R5);
            R0 = ADC(R0, LR);
            R1 = ADDS(R1, R2);
            R2 = v_390;
            R0 = ADC(R0, R11);
            R4 = R8;
            LR = ADDS(R2, R1);
            R2 = v_38C;
            R6 = R3;
            R12 = ADC(R2, R0);
            R2 = v_384;
            R8 = ADDS(R1, R2);
            R2 = v_36C;
            R5 = ADC(R0, R2);
        }

        long[] list_638 = new long[]{check(LR), check(R12), check(v_39C), check(v_398),
                check(v_378), check(v_374), check(v_3A4), check(v_3A0),
                check(R8), check(R5), check(v_3A8), check(v_3AC),
                check(R9), check(R10), check(R6), check(R4)};

        for (int i = 0; i < 8; i++) {
            long R0 = param_list[2 * i];
            long R1 = param_list[2 * i + 1];
            R0 = ADDS(R0, list_638[2 * i]);
            R1 = ADCS(R1, list_638[2 * i + 1]);
            param_list[2 * i] = R0;
            param_list[2 * i + 1] = R1;
        }
        //check_log(param_list)
        return param_list;
    }

    private long[] hex_CF8(long[] param_list) {
        long[] list_388 = new long[]{};
        long[] list_378 = param_list;
        for (int i = 0; i < 0xA; i++) {
            long R3 = list_378[0];
            long R8 = list_378[1];
            long R9 = list_378[2];
            long R5 = list_378[3];
            long R6 = UBFX(R5, 8, 8);
            R6 = dword_0[Long.valueOf(R6).intValue()];
            R6 = Long.parseLong(Long.toHexString(R6) + "0000", 16);
            long R4 = UBFX(R5, 0x10, 8);
            long R11 = dword_1[i];
            R4 = dword_0[Long.valueOf(R4).intValue()];
            R4 = Long.parseLong(Long.toHexString(R4) + "000000", 16);
            R3 = R3 ^ R4;
            R4 = UTFX(R5);
            R3 = R3 ^ R6;
            R4 = dword_0[Long.valueOf(R4).intValue()];
            R4 = Long.parseLong(Long.toHexString(R4) + "00", 16);
            R3 = R3 ^ R4;
            R4 = R5 >> 24;
            R4 = dword_0[Long.valueOf(R4).intValue()];
            R3 = R3 ^ R4;
            R3 = R3 ^ R11;
            long R2 = R8 ^ R3;
            R4 = R9 ^ R2;
            R5 = R5 ^ R4;
            list_378 = new long[]{R3, R2, R4, R5};
            list_388 = ArrayUtils.addAll(list_388, list_378);
        }
        int l388l = list_388.length;
        List<Long> list_478 = new ArrayList<>();
        for (int i = 0; i < 0x9; i++) {
            long R5 = list_388[l388l - 8 - 4 * i];
            long R4 = UBFX(R5, 0x10, 8);
            long R6 = R5 >> 0x18;
            R6 = dword_2[Long.valueOf(R6).intValue()];
            R4 = dword_3[Long.valueOf(R4).intValue()];
            R6 = R6 ^ R4;
            R4 = UBFX(R5, 8, 8);
            R5 = UTFX(R5);
            R4 = dword_4[Long.valueOf(R4).intValue()];
            R5 = dword_5[Long.valueOf(R5).intValue()];
            R6 = R6 ^ R4;
            R6 = R6 ^ R5;
            list_478.add(R6);
            R6 = list_388[l388l - 7 - 4 * i];
            long R1 = UBFX(R6, 0x10, 8);
            R4 = R6 >> 0x18;
            R4 = dword_2[Long.valueOf(R4).intValue()];
            R1 = dword_3[Long.valueOf(R1).intValue()];
            R1 = R1 ^ R4;
            R4 = UBFX(R6, 8, 8);
            R4 = dword_4[Long.valueOf(R4).intValue()];
            R1 = R1 ^ R4;
            R4 = UTFX(R6);
            R4 = dword_5[Long.valueOf(R4).intValue()];
            R1 = R1 ^ R4;
            list_478.add(R1);
            R1 = list_388[l388l - 6 - 4 * i];
            R6 = UBFX(R1, 0x10, 8);
            R4 = R1 >> 0x18;
            R4 = dword_2[Long.valueOf(R4).intValue()];
            R6 = dword_3[Long.valueOf(R6).intValue()];
            R4 = R4 ^ R6;
            R6 = UBFX(R1, 8, 8);
            R1 = UTFX(R1);
            R6 = dword_4[Long.valueOf(R6).intValue()];
            R1 = dword_5[Long.valueOf(R1).intValue()];
            R4 = R4 ^ R6;
            R1 = R1 ^ R4;
            list_478.add(R1);
            long R0 = list_388[l388l - 5 - 4 * i];
            R1 = UTFX(R0);
            R4 = UBFX(R0, 8, 8);
            R6 = R0 >> 0x18;
            R0 = UBFX(R0, 0x10, 8);
            R6 = dword_2[Long.valueOf(R6).intValue()];
            R0 = dword_3[Long.valueOf(R0).intValue()];
            R4 = dword_4[Long.valueOf(R4).intValue()];
            R1 = dword_5[Long.valueOf(R1).intValue()];
            R0 = R0 ^ R6;
            R0 = R0 ^ R4;
            R0 = R0 ^ R1;
            list_478.add(R0);
        }

        long[] list_468 = ArrayUtils.addAll(param_list, list_388);
        return list_468;

    }

    public long[] hex_C52(long[] list_6B0) {
        long[] list_8D8 = new long[]{};
        for (int i = 0; i < 8; i++) {
            long[] tmp = hex_list(new long[]{list_6B0[2 * i + 1], list_6B0[2 * i]});
            list_8D8 = ArrayUtils.addAll(list_8D8, tmp);
        }
        return list_8D8;
    }

    public long[] handle_ending(long num, long R0) {
        String s = Long.toHexString(num);
        long R1;
        long R2;
        if (s.length() <= 8) {
            R1 = num;
            R2 = 0;
        } else {
            String numStr = Long.toHexString(num);
            int length = numStr.length();
            R1 = Long.parseLong(numStr.substring(length - 8), 16);
            R2 = Long.parseLong(numStr.substring(2, length - 8), 16);
        }
        R1 = ADDS(R1, R0 << 3);
        R2 = ADC(R2, R0 >> 29);
        return hex_list(new long[]{R2, R1});
    }


    private String model;

    private long[] content;

    private long[] list_9C8 = hex_9C8();

    private long[] beginning = new long[]{0x74, 0x63, 0x05, 0x10, 0, 0};

    private long[] list_6B0;


    private long[] prepare() {
        return this.content;
    }

    public long[] calculate(long[] content) {
        long hex_6A8 = 0l;
        List<Long> tmp_list = new ArrayList<>();
        int length = content.length;
        long[] list_6B0 = (long[]) deepCopy(this.list_6B0);
        for (long item : content) {
            tmp_list.add(item);
        }
        int divisible = length % 0x80;
        int tmp = 0x80 - divisible;
        if (tmp > 0x11) {
            tmp_list.add(Integer.valueOf(0x80).longValue());
            for (int i = 0; i < (tmp - 0x11); i++) {
                tmp_list.add(0L);
            }
            tmp_list.addAll(Lists.newArrayList(0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L));
        } else {
            tmp_list.add(0x80L);
            for (int i = 0; i < (0x80 - 0x10 + tmp - 1); i++) {
                tmp_list.add(0L);
            }
            tmp_list.addAll(Lists.newArrayList(0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L));
        }

        int tmpListSize = tmp_list.size();
        for (int i = 0; i < tmpListSize / 0x80; i++) {
            if (i == (tmpListSize / 0x80 - 1)) {
                long[] ending = handle_ending(hex_6A8, divisible);
                for (int j = 0; j < 8; j++) {
                    int index = tmpListSize - j - 1;
                    tmp_list.set(index, ending[7 - j]);
                }
            }
            List<Long> param_list = Lists.newArrayList();
            for (int j = 0; j < 0x80 / 4; j++) {
                String tmpss = "";
                for (int k = 0; k < 4; k++) {
                    String tmp_string = Long.toHexString(tmp_list.get(0x80 * i + 0x4 * j + k));
                    if (tmp_string.length() < 2) {
                        tmp_string = "0" + tmp_string;
                    }
                    tmpss = tmpss + tmp_string;
                }
                param_list.add(Long.parseLong(tmpss, 16));
            }

            long[] param_listArray = param_list.stream().mapToLong(Long::valueOf).toArray();
            long[] list_3B8 = hex_27E(param_listArray);
            list_6B0 = hex_30A(list_6B0, list_3B8);
            hex_6A8 += 0x400;
        }
        long[] list_8D8 = hex_C52(list_6B0);
        return list_8D8;
    }

    private long[] encrypt() {
        long[] list_0B0 = ArrayUtils.addAll(calculate(this.list_9C8), ord_list);
        long[] list_5D8 = this.calculate(list_0B0);
        System.out.println(JSON.toJSONString(list_5D8));
        List<Long> list_378 = Lists.newArrayList();
        List<Long> list_740 = Lists.newArrayList();
        for (int i = 0x0; i < 0x10; i++) {
            list_378.add(list_5D8[i]); // key
        }
        long[] list_378Array = list_378.stream().mapToLong(Long::valueOf).toArray();
        list_378Array = dump_list(list_378Array);
        for (int i = 0x10; i < 0x20; i++) {
            list_740.add(list_5D8[i]); // iv
        }
        content = this.prepare();
        long[] list_8D8 = this.calculate(content);
        long[] list_AB0 = ArrayUtils.addAll(list_8D8, content);
        List<Long> list_AB0List = convertLongList(list_AB0);
        int differ = 0x10 - list_AB0.length % 0x10;
        for (int i = 0; i < differ; i++) {
            list_AB0List.add(Integer.valueOf(differ).longValue());
        }
        list_AB0 = convertLongArray(list_AB0List);
        long[] list_55C = hex_CF8(list_378Array);
        long[] final_list = hex_0A2(list_AB0, convertLongArray(list_740), list_55C);
        final_list = ArrayUtils.addAll(ArrayUtils.addAll(this.beginning, this.list_9C8), final_list);
        return final_list;
    }

    private static int choice(int start, int end) {
        int a = (int) (Math.random() * (end - start) + start);
        return a;
    }


    private List<Long> convertLongList(long[] content) {
        if (ArrayUtils.isEmpty(content)) {
            return Lists.newArrayList();
        }
        List<Long> result = Lists.newArrayList();
        for (long l : content) {
            result.add(l);
        }
        return result;
    }

    private long[] convertLongArray(List<Long> arrays) {
        long[] tmp_listInt = arrays.stream().mapToLong(Long::valueOf).toArray();
        return tmp_listInt;
    }


    public static Object deepCopy(Object from) {
        Object obj = null;
        try {
            // 将对象写成 Byte Array
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            ObjectOutputStream out = new ObjectOutputStream(bos);
            out.writeObject(from);
            out.flush();
            out.close();

            ObjectInputStream in = new ObjectInputStream(
                    new ByteArrayInputStream(bos.toByteArray()));
            obj = in.readObject();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (ClassNotFoundException e2) {
            e2.printStackTrace();
        }
        return obj;
    }

    public static final String GZIP_ENCODE_UTF_8 = "UTF-8";

    public static byte[] compress(String str) {
        return compress(str, GZIP_ENCODE_UTF_8);
    }


    public static byte[] compress(String str, String encoding) {
        if (str == null || str.length() == 0) {
            return null;
        }
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            compress(str.getBytes(encoding), out);
            return out.toByteArray();
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        return null;

    }

    /**
     * 数据压缩到输出流
     *
     * @param data 字节数组
     * @param out  输出流
     * @param <T>  extends OutputStream
     */
    public static <T extends OutputStream> void compress(byte[] data, T out) {
        GZIPOutputStream gos = null;
        try {
            gos = new GZIPOutputStream(out);
            gos.write(data);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                gos.close();
            } catch (IOException e) {
            }
        }
    }


    public static byte[] getEn(byte[] bytes) throws IOException {
        long[] content = changeToLongArray(bytes);
        long[] encrypt = new TT2Encrypt(content, null).encrypt();
        return changeLongArrayTobytes(encrypt);
    }


    public static byte[] ttEncryptNew(byte[] bytes) {
        long[] content = changeToLongArray(bytes);
        long[] encrypt = new TT2Encrypt(content, null).encrypt();
        return changeLongArrayTobytes(encrypt);
    }

    private static byte[] changeLongArrayTobytes(long[] arrays) {
        byte[] result = new byte[arrays.length];
        for (int i = 0; i < arrays.length; i++) {
            result[i] = (byte) arrays[i];
        }
        return result;
    }

    public static long[] changeToLongArray(byte[] bytes) {
        long[] result = new long[bytes.length];
        for (int i = 0; i < bytes.length; i++) {
            if (bytes[i] < 0) {
                result[i] = bytes[i] + 256;
            } else {
                result[i] = bytes[i];
            }
        }
        return result;
    }


    public static void main(String[] args) throws IOException {
        for (int i = 0; i < 2; i++) {
            //String sss = "{\"magic_tag\":\"ss_app_log\",\"header\":{\"sdk_version\":231,\"language\":\"ru-RU\",\"user_agent\":\"TikTok 16.6.2 rv:166206 (iPhone; iOS 13.5; ru_RU) Cronet\",\"app_name\":\"musical_ly\",\"app_version\":\"16.6.2\",\"vendor_id\":\"8CE2FEB5-14EC-4082-89E5-7399E6605B99\",\"is_upgrade_user\":true,\"region\":\"RU\",\"channel\":\"App Store\",\"mcc_mnc\":\"25099\",\"tz_offset\":10800,\"app_region\":\"RU\",\"resolution\":\"828*1792\",\"aid\":\"1233\",\"os\":\"iOS\",\"device_id\":\"6742693107483248133\",\"custom\":{\"app_region\":\"RU\",\"earphone_status\":\"off\",\"web_ua\":\"Mozilla\\/5.0 (iPhone; CPU iPhone OS 13_5 like Mac OS X) AppleWebKit\\/605.1.15 (KHTML, like Gecko) Mobile\\/15E148\",\"filter_warn\":0,\"user_period\":0,\"user_mode\":1,\"app_language\":\"ru\",\"build_number\":\"166206\"},\"access\":\"WIFI\",\"install_id\":\"6849746715186267909\",\"cdid\":\"1EEDDDD3-9476-427C-9EE2-1B649FE510B1\",\"carrier\":\"Beeline\",\"model_display_name\":false,\"os_version\":\"13.5\",\"device_model\":\"iPhone11,8\",\"timezone\":3,\"display_name\":\"TikTok\",\"package\":\"com.zhiliaoapp.musically\",\"model_display_name\":\"iPhone XR\",\"tz_name\":\"Europe\\/Moscow\",\"app_language\":\"ru\",\"idfa\":\"F565629A-BBD6-4853-AF0A-3312A7197F52\"}}";
            //String sss = "{\"iv\":\"2168C93B-C50C-4D\",\"aid\":\"1233\",\"key\":\"438CB4AD-278F-40D0-821D-FCB7B8D2\",\"lang\":\"en\",\"appkey\":\"\",\"locale\":\"en_FR\",\"channel\":\"App Store\",\"os_name\":\"iOS\",\"os_type\":\"1\",\"os_version\":\"13.6\",\"resolution\":\"1242*2208\",\"app_version\":\"18.4.5\",\"sdk_version\":\"1.8.0\",\"device_brand\":\"iPhone\",\"device_model\":\"iPhone8,2\"}";
            String sss = "{\"iv\":\"ECF4B538-D4D9-4D\",\"aid\":\"1233\",\"did\":\"6922833125249074694\",\"iid\":\"6922833362709874437\",\"key\":\"BBF87F65-C96F-4E76-8D42-BF5208E5\",\"lang\":\"en\",\"appkey\":\"\",\"locale\":\"en_FR\",\"channel\":\"App Store\",\"os_name\":\"iOS\",\"os_type\":\"1\",\"os_version\":\"13.6\",\"resolution\":\"1242*2208\",\"app_version\":\"18.4.5\",\"sdk_version\":\"1.8.0\",\"device_brand\":\"iPhone\",\"device_model\":\"iPhone8,2\"}";
//            byte[] bytes = compress(sss);
//            long[] content = changeToLongArray(bytes);
            byte[] bytes = sss.getBytes();
            long[] content = changeToLongArray(bytes);
            String encrypt = new String(changeLongArrayTobytes(new TT2Encrypt(content, null).encrypt()));
            //System.out.println(encrypt);
        }
    }

}
