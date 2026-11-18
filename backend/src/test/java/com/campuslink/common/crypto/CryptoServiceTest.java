package com.campuslink.common.crypto;

import com.campuslink.config.AppProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CryptoServiceTest {

    private final CryptoService cryptoService = new CryptoService(props());

    private static AppProperties props() {
        AppProperties props = new AppProperties();
        props.getCrypto().setHashKey("test-hash-key-0123456789abcdef0123456789abcdef");
        props.getCrypto().setCryptKey("test-crypt-key-0123456789abcdef0123456789abcdef");
        return props;
    }

    @Test
    @DisplayName("HMAC 哈希确定性、定长且可区分输入")
    void hashIsDeterministicAndDifferentiatesInputs() {
        String a1 = cryptoService.hash("2023001");
        String a2 = cryptoService.hash("2023001");
        String b = cryptoService.hash("2023002");
        assertThat(a1).isEqualTo(a2).hasSize(64).isNotEqualTo(b);
    }

    @Test
    @DisplayName("AES-GCM 随机 IV：同明文两次密文不同，均可解回")
    void encryptIsRandomizedAndDecryptable() {
        String enc1 = cryptoService.encrypt("2023001");
        String enc2 = cryptoService.encrypt("2023001");
        assertThat(enc1).isNotEqualTo(enc2);
        assertThat(cryptoService.decrypt(enc1)).isEqualTo("2023001");
        assertThat(cryptoService.decrypt(enc2)).isEqualTo("2023001");
    }

    @Test
    @DisplayName("弱密钥（< 32 字节）在启动期被拒绝")
    void weakKeyRejected() {
        AppProperties weak = props();
        weak.getCrypto().setHashKey("short");
        assertThatThrownBy(() -> new CryptoService(weak))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("姓名归一化：去空格、全角转半角、小写")
    void nameNormalizer() {
        assertThat(NameNormalizer.normalize(" 张三 ")).isEqualTo("张三-CI实验");
        assertThat(NameNormalizer.normalize("ＯＬＬＥｈ")).isEqualTo("olleh");
        assertThat(NameNormalizer.normalize(null)).isEmpty();
    }
}
