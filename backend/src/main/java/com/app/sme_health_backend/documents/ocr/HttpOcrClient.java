package com.app.sme_health_backend.documents.ocr;

import java.io.IOException;
import java.util.Locale;
import java.util.Objects;

/** One request only: provider retries belong exclusively to the Python extraction service. */
public final class HttpOcrClient implements OcrClient {
    private final OcrClientSettings settings;
    private final OcrHttpTransport transport;
    private final OcrJsonCodec codec;

    public HttpOcrClient(OcrClientSettings settings, OcrHttpTransport transport, OcrJsonCodec codec) {
        this.settings = Objects.requireNonNull(settings);
        this.transport = Objects.requireNonNull(transport);
        this.codec = Objects.requireNonNull(codec);
    }

    @Override
    public OcrExtraction extract(OcrRequest request) {
        Objects.requireNonNull(request);
        OcrHttpTransport.Response response;
        try {
            response = transport.post(settings.extractEndpoint(), codec.encode(request), settings.requestTimeout(),
                    settings.maxResponseBytes());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new OcrClientException(OcrClientException.Reason.unavailable);
        } catch (IOException exception) {
            throw new OcrClientException(OcrClientException.Reason.unavailable);
        }
        if (response.statusCode() != 200) {
            throw new OcrClientException(response.statusCode() == 400 || response.statusCode() == 422
                    ? OcrClientException.Reason.invalid_request : OcrClientException.Reason.unavailable);
        }
        if (response.contentType() == null
                || !response.contentType().split(";", 2)[0].strip().toLowerCase(Locale.ROOT).equals("application/json")
                || response.body() == null || response.body().length > settings.maxResponseBytes()) {
            throw new OcrClientException(OcrClientException.Reason.invalid_response);
        }
        return codec.decode(response.body());
    }
}
