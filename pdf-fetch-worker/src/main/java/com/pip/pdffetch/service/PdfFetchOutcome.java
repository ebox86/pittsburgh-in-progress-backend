package com.pip.pdffetch.service;

import com.google.cloud.storage.Blob;

public record PdfFetchOutcome(Blob blob, PdfFetchStatus status) {
}
