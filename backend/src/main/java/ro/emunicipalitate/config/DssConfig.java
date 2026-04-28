package ro.emunicipalitate.config;


import eu.europa.esig.dss.service.http.commons.CommonsDataLoader;
import eu.europa.esig.dss.service.http.commons.OCSPDataLoader;
import eu.europa.esig.dss.service.http.commons.TimestampDataLoader;
import eu.europa.esig.dss.service.tsp.OnlineTSPSource;
import eu.europa.esig.dss.service.ocsp.OnlineOCSPSource;
import eu.europa.esig.dss.service.crl.OnlineCRLSource;
import eu.europa.esig.dss.spi.x509.CommonTrustedCertificateSource;
import eu.europa.esig.dss.spi.validation.CommonCertificateVerifier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configures the EU Digital Signature Services (DSS) components
 * required for PAdES-B-LTA signing and validation.
 *
 * <ul>
 *   <li><b>TSP Source</b> — RFC 3161 timestamp authority for signature timestamps</li>
 *   <li><b>OCSP Source</b> — Online certificate revocation checks</li>
 *   <li><b>CRL Source</b> — Certificate revocation list retrieval</li>
 *   <li><b>Certificate Verifier</b> — Aggregates trust anchors and revocation sources</li>
 * </ul>
 */
@Configuration
@Slf4j
public class DssConfig {

    @Value("${app.signing.tsa-url}")
    private String tsaUrl;

    @Bean
    public OnlineTSPSource onlineTSPSource() {
        OnlineTSPSource tspSource = new OnlineTSPSource(tsaUrl);
        tspSource.setDataLoader(new TimestampDataLoader());
        log.info("EU DSS TSP source configured: {}", tsaUrl);
        return tspSource;
    }

    @Bean
    public OnlineOCSPSource onlineOCSPSource() {
        OnlineOCSPSource ocspSource = new OnlineOCSPSource();
        ocspSource.setDataLoader(new OCSPDataLoader());
        return ocspSource;
    }

    @Bean
    public OnlineCRLSource onlineCRLSource() {
        return new OnlineCRLSource(new CommonsDataLoader());
    }

    @Bean
    public CommonCertificateVerifier certificateVerifier(OnlineOCSPSource ocspSource,
                                                         OnlineCRLSource crlSource) {
        CommonCertificateVerifier verifier = new CommonCertificateVerifier();
        verifier.setOcspSource(ocspSource);
        verifier.setCrlSource(crlSource);
        verifier.setTrustedCertSources(new CommonTrustedCertificateSource());
        return verifier;
    }
}
