package org.tron.common.crypto.sm2;

import org.spongycastle.asn1.ASN1InputStream;
import org.spongycastle.asn1.ASN1Integer;
import org.spongycastle.asn1.DLSequence;
import org.spongycastle.crypto.AsymmetricCipherKeyPair;
import org.spongycastle.crypto.CipherParameters;
import org.spongycastle.crypto.digests.SHA256Digest;
import org.spongycastle.crypto.generators.ECKeyPairGenerator;
import org.spongycastle.crypto.params.*;
import org.spongycastle.crypto.signers.*;
import org.spongycastle.jcajce.provider.asymmetric.ec.BCECPrivateKey;
import org.spongycastle.jcajce.provider.asymmetric.ec.BCECPublicKey;
import org.spongycastle.jce.spec.ECParameterSpec;
import org.spongycastle.jce.spec.ECPrivateKeySpec;
import org.spongycastle.math.ec.*;
import org.spongycastle.util.encoders.Base64;
import org.spongycastle.util.encoders.Hex;
import org.tron.common.crypto.ECKey;
import org.tron.common.crypto.jce.ECKeyFactory;
import org.tron.common.crypto.jce.ECSignatureFactory;
import org.tron.common.crypto.jce.TronCastleProvider;
import org.tron.common.utils.ByteUtil;

import javax.annotation.Nullable;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.Charset;
import java.security.*;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.InvalidKeySpecException;

import static org.tron.common.utils.BIUtil.isLessThan;
import static org.tron.common.utils.ByteUtil.bigIntegerToBytes;

/**
 * Implement Chinese Commercial Cryptographic Standard of SM2
 *
 */
public class SM2 {
    private static BigInteger SM2_N = new BigInteger("FFFFFFFEFFFFFFFFFFFFFFFFFFFFFFFF7203DF6B21C6052B53BBF40939D54123", 16);
    private static BigInteger SM2_P = new BigInteger("FFFFFFFEFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF00000000FFFFFFFFFFFFFFFF", 16);
    private static BigInteger SM2_A = new BigInteger("FFFFFFFEFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF00000000FFFFFFFFFFFFFFFC", 16);
    private static BigInteger SM2_B = new BigInteger("28E9FA9E9D9F5E344D5A9E4BCF6509A7F39789F515AB8F92DDBCBD414D940E93", 16);
    private static BigInteger SM2_GX = new BigInteger("32C4AE2C1F1981195F9904466A39C9948FE30BBFF2660BE1715A4589334C74C7", 16);
    private static BigInteger SM2_GY = new BigInteger("BC3736A2F4F6779C59BDCEE36B692153D0A9877CC62A474002DF32E52139F0A0", 16);

    private static ECDomainParameters ecc_param;
    private static ECParameterSpec ecc_spec;
    private static ECCurve.Fp curve;
    private static ECPoint ecc_point_g;

    private static final SecureRandom secureRandom;



    static {
        secureRandom = new SecureRandom();
        curve = new ECCurve.Fp(SM2_P, SM2_A, SM2_B);
        ecc_point_g = curve.createPoint(SM2_GX, SM2_GY);
        ecc_param = new ECDomainParameters(curve, ecc_point_g, SM2_N);
        ecc_spec = new ECParameterSpec(curve, ecc_point_g, SM2_N);
    }

    protected final ECPoint pub;

    private final PrivateKey privKey;

    private SM2KeyPair keyPair;

//    private final DSAKCalculator kCalculator = new RandomDSAKCalculator();
//    private byte[] userID;

    public SM2() {
        this(secureRandom);
    }
    /**
     * Generates an entirely new keypair.
     *
     * <p>BouncyCastle will be used as the Java Security Provider
     */


    /**
     * Generate a new keypair using the given Java Security Provider.
     *
     * <p>All private key operations will use the provider.
     */
    public SM2(SecureRandom secureRandom) {

        ECKeyGenerationParameters ecKeyGenerationParameters = new ECKeyGenerationParameters(ecc_param, secureRandom);
        ECKeyPairGenerator keyPairGenerator = new ECKeyPairGenerator();
        keyPairGenerator.init(ecKeyGenerationParameters);
        AsymmetricCipherKeyPair kp = keyPairGenerator.generateKeyPair();
        ECPrivateKeyParameters ecpriv = (ECPrivateKeyParameters) kp.getPrivate();
        ECPublicKeyParameters ecpub = (ECPublicKeyParameters) kp.getPublic();

        BigInteger privateKey = ecpriv.getD();
        this.privKey = privateKeyFromBigInteger(privateKey);
        this.pub = ecpub.getQ();
        this.keyPair = new SM2KeyPair(pub.getEncoded(false),privateKey.toByteArray());

//        CipherParameters privateKeyParameters = new ECPrivateKeyParameters(privateKey, ecc_param);
//        CipherParameters baseParam;
//
//        if (privateKeyParameters instanceof ParametersWithID)
//        {
//            baseParam = ((ParametersWithID)privateKeyParameters).getParameters();
//            userID = ((ParametersWithID)privateKeyParameters).getID();
//        }
//        else
//        {
//            baseParam = privateKeyParameters;
//            userID = new byte[0];
//        }
//        this.kCalculator.init(SM2_N, secureRandom);
    }


//    /**
//     * Pair a private key with a public EC point.
//     *
//     * <p>All private key operations will use the provider.
//     */
//
//    public SM2(@Nullable PrivateKey privKey, ECPoint pub) {
//
//        if (privKey == null || isECPrivateKey(privKey)) {
//            this.privKey = privKey;
//        } else {
//            throw new IllegalArgumentException(
//                    "Expected EC private key, given a private key object with" +
//                            " class "
//                            + privKey.getClass().toString() +
//                            " and algorithm "
//                            + privKey.getAlgorithm());
//        }
//
//        if (pub == null) {
//            throw new IllegalArgumentException("Public key may not be null");
//        } else {
//            this.pub = pub;
//        }
//    }

    /**
     * Pair a private key integer with a public EC point
     *
     */
    public SM2(@Nullable BigInteger priv, ECPoint pub) {

        this.privKey = privateKeyFromBigInteger(priv);
        this.pub = pub;
        this.keyPair = new SM2KeyPair(pub.getEncoded(false), priv.toByteArray());

    }

    /**
     * Convert a BigInteger into a PrivateKey object
     *
     * @param priv
     * @return
     */
    private static PrivateKey privateKeyFromBigInteger(BigInteger priv) {
        if (priv == null) {
            return null;
        } else {
            try {
                return ECKeyFactory
                        .getInstance(TronCastleProvider.getInstance())
                        .generatePrivate(new ECPrivateKeySpec(priv,
                                ecc_spec));
            } catch (InvalidKeySpecException ex) {
                throw new AssertionError("Assumed correct key spec statically");
            }
        }
    }

    /* Test if a generic private key is an EC private key
     *
     * it is not sufficient to check that privKey is a subtype of ECPrivateKey
     * as the SunPKCS11 Provider will return a generic PrivateKey instance
     * a fallback that covers this case is to check the key algorithm
     */
    private static boolean isECPrivateKey(PrivateKey privKey) {
        return privKey instanceof ECPrivateKey || privKey.getAlgorithm()
                .equals("EC");
    }


    /**
     * Signs the given hash and returns the R and S components as BigIntegers and putData them in
     * SM2Signature
     *
     * @param input to sign
     * @return SM2Signature signature that contains the R and S components
     */
    public SM2.SM2Signature signHash(byte[] input) {
        if (input.length != 32) {
            throw new IllegalArgumentException("Expected 32 byte input to " +
                    "SM2 signature, not " + input.length);
        }
        // No decryption of private key required.
        SM2Signer signer = getSigner();
        BigInteger[] componets =  signer.generateHashSignature(input);
        return new SM2.SM2Signature(componets[0], componets[1]);
    }


    /**
     * Signs the given hash and returns the R and S components as BigIntegers and putData them in
     * SM2Signature
     *
     * @param msg to sign
     * @return SM2Signature signature that contains the R and S components
     */
    public SM2.SM2Signature signMessage(byte[] msg) {
        if (null == msg) {
            throw new IllegalArgumentException("Expected 32 byte input to " +
                    "SM2 signature, not " + msg.length);
        }
        // No decryption of private key required.
        SM2Signer signer = getSigner();
        BigInteger[] componets =  signer.generateSignature(msg);
        return new SM2.SM2Signature(componets[0], componets[1]);
    }

    private SM2Signer getSigner() {
        if (this.keyPair == null) {
            throw new ECKey.MissingPrivateKeyException();
        }
        SM2Signer signer = new SM2Signer();
        BigInteger d = byte2BigInteger(this.keyPair.getPrivatekey());
        ECPrivateKeyParameters privateKeyParameters = new ECPrivateKeyParameters(d, ecc_param);
        signer.init(true,privateKeyParameters);
        return signer;
    }


    /**
     * generate the key pair of SM2
     *
     * @return
     */
    public SM2KeyPair generateKeyPair() {
        ECKeyGenerationParameters ecKeyGenerationParameters = new ECKeyGenerationParameters(ecc_param, new SecureRandom());
        ECKeyPairGenerator keyPairGenerator = new ECKeyPairGenerator();
        keyPairGenerator.init(ecKeyGenerationParameters);
        AsymmetricCipherKeyPair kp = keyPairGenerator.generateKeyPair();
        ECPrivateKeyParameters ecpriv = (ECPrivateKeyParameters) kp.getPrivate();
        ECPublicKeyParameters ecpub = (ECPublicKeyParameters) kp.getPublic();

        BigInteger privateKey = ecpriv.getD();

        ECPoint publickey = ecpub.getQ();

        return new SM2KeyPair(publickey.getEncoded(false),privateKey.toByteArray());
    }

    /**
     * transfer byte array to BigInteger
     *
     * @param b byte array input
     * @return output the big integer
     */
    public static BigInteger byte2BigInteger(byte[] b) {
        if (b[0] < 0) {
            byte[] tmp = new byte[b.length + 1];
            tmp[0] = 0;
            System.arraycopy(b, 0, tmp, 1, b.length);
            return new BigInteger(tmp);
        }
        return new BigInteger(b);
    }

    /**
     * transfer the byte array to ECPoint
     *
     * @param publicKey
     * @return
     */
    public static ECPoint byte2ECPoint(byte[] publicKey) {
        byte[] formatedPubKey;
        if (publicKey.length == 64) {
            formatedPubKey = new byte[55];
            formatedPubKey[0] = 0x04;
            System.arraycopy(publicKey,0,formatedPubKey,1,publicKey.length);
        } else {
            formatedPubKey = publicKey;
        }
        ECPoint userKey = curve.decodePoint(formatedPubKey);
        return userKey;
    }

    /**
     * generate the signature
     *
     * @param privateKey
     * @param msg
     * @return output the signature r and s
     * @throws Exception
     */
    public BigInteger[] sign(byte[] privateKey, byte[] msg) throws Exception {
       if (null == privateKey) {
           throw new Exception("private key is null");
       }
       if (privateKey.length == 0) {
           throw new Exception("the length of private is 0");
       }
       if (null == msg) {
           throw new Exception("plaintext is null");
       }
       if (msg.length == 0) {
           throw new Exception("the length of plaintext is 0");
       }
       SM2Signer signer = new SM2Signer();
       BigInteger d = byte2BigInteger(privateKey);
       ECPrivateKeyParameters privateKeyParameters = new ECPrivateKeyParameters(d, ecc_param);
       signer.init(true,privateKeyParameters);
       return signer.generateSignature(msg);
    }

    public boolean verify(byte[] publicKey, BigInteger[] signVaule, byte[] msg) throws Exception {
        if (null == publicKey) {
            throw new Exception("public key is null");
        }
        if (publicKey.length == 0) {
            throw new Exception("the length of public key is 0");
        }
        if (null == signVaule) {
            throw new Exception("signValue is null");
        }
        if (signVaule.length != 2) {
            throw new Exception("length of signValue is not 2");
        }
        if (null == msg) {
            throw new Exception("plaintext is null");
        }
        if (msg.length == 0) {
            throw new Exception("the length of plaintext is 0");
        }
        SM2Signer signer = new SM2Signer();
        ECPublicKeyParameters ecPub = new ECPublicKeyParameters(byte2ECPoint(publicKey),ecc_param);
        signer.init(false, ecPub);
        return signer.verifySignature(msg, signVaule[0], signVaule[1]);
    }

    public static class SM2Signature {

        /**
         * The two components of the signature.
         */
        public final BigInteger r, s;
        public byte v;

        /**
         * Constructs a signature with the given components. Does NOT automatically canonicalise the
         * signature.
         *
         * @param r -
         * @param s -
         */
        public SM2Signature(BigInteger r, BigInteger s) {
            this.r = r;
            this.s = s;
        }

        /**
         * t
         *
         * @return -
         */
        private static SM2.SM2Signature fromComponents(byte[] r, byte[] s) {
            return new SM2.SM2Signature(new BigInteger(1, r), new BigInteger(1,
                    s));
        }

        /**
         * @param r -
         * @param s -
         * @param v -
         * @return -
         */
        public static SM2.SM2Signature fromComponents(byte[] r, byte[] s, byte
                v) {
            SM2.SM2Signature signature = fromComponents(r, s);
            signature.v = v;
            return signature;
        }

        public static boolean validateComponents(BigInteger r, BigInteger s,
                                                 byte v) {

            if (v != 27 && v != 28) {
                return false;
            }

            if (isLessThan(r, BigInteger.ONE)) {
                return false;
            }
            if (isLessThan(s, BigInteger.ONE)) {
                return false;
            }

            if (!isLessThan(r, SM2.SM2_N)) {
                return false;
            }
            return isLessThan(s, SM2.SM2_N);
        }

        public static SM2.SM2Signature decodeFromDER(byte[] bytes) {
            ASN1InputStream decoder = null;
            try {
                decoder = new ASN1InputStream(bytes);
                DLSequence seq = (DLSequence) decoder.readObject();
                if (seq == null) {
                    throw new RuntimeException("Reached past end of ASN.1 " +
                            "stream.");
                }
                ASN1Integer r, s;
                try {
                    r = (ASN1Integer) seq.getObjectAt(0);
                    s = (ASN1Integer) seq.getObjectAt(1);
                } catch (ClassCastException e) {
                    throw new IllegalArgumentException(e);
                }
                // OpenSSL deviates from the DER spec by interpreting these
                // values as unsigned, though they should not be
                // Thus, we always use the positive versions. See:
                // http://r6.ca/blog/20111119T211504Z.html
                return new SM2.SM2Signature(r.getPositiveValue(), s
                        .getPositiveValue());
            } catch (IOException e) {
                throw new RuntimeException(e);
            } finally {
                if (decoder != null) {
                    try {
                        decoder.close();
                    } catch (IOException x) {

                    }
                }
            }
        }

        public boolean validateComponents() {
            return validateComponents(r, s, v);
        }


        /**
         * @return -
         */
        public String toBase64() {
            byte[] sigData = new byte[65];  // 1 header + 32 bytes for R + 32
            // bytes for S
            sigData[0] = v;
            System.arraycopy(bigIntegerToBytes(this.r, 32), 0, sigData, 1, 32);
            System.arraycopy(bigIntegerToBytes(this.s, 32), 0, sigData, 33, 32);
            return new String(Base64.encode(sigData), Charset.forName("UTF-8"));
        }

        public byte[] toByteArray() {
            final byte fixedV = this.v >= 27
                    ? (byte) (this.v - 27)
                    : this.v;

            return ByteUtil.merge(
                    ByteUtil.bigIntegerToBytes(this.r, 32),
                    ByteUtil.bigIntegerToBytes(this.s, 32),
                    new byte[]{fixedV});
        }

        public String toHex() {
            return Hex.toHexString(toByteArray());
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            SM2.SM2Signature signature = (SM2.SM2Signature) o;

            if (!r.equals(signature.r)) {
                return false;
            }
            return s.equals(signature.s);
        }

        @Override
        public int hashCode() {
            int result = r.hashCode();
            result = 31 * result + s.hashCode();
            return result;
        }
    }


}
