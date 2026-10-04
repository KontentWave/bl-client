<?php

// Load only the actual pure services: no Laravel boot, .env, database, cache, HTTP or SMS.
$root = $argv[1] ?? '';
if (! is_file($root.'/app/Services/EscortPhoneNumberNormalizer.php')
    || ! is_file($root.'/app/Services/DeviceSignatureService.php')) {
    fwrite(STDERR, "Missing explicitly selected backend service sources.\n");
    exit(2);
}
require $root.'/app/Services/EscortPhoneNumberNormalizer.php';
require $root.'/app/Services/DeviceSignatureService.php';

$fixtures = json_decode(stream_get_contents(STDIN), true, flags: JSON_THROW_ON_ERROR);
$normalizer = new App\Services\EscortPhoneNumberNormalizer;
$verifier = new App\Services\DeviceSignatureService;
$reportCount = 0;
$queryCount = 0;

foreach ($fixtures as $fixture) {
    $request = $fixture['request'];
    $number = $normalizer->normalizeE164($request['client_phone_number']);
    $rawNumber = $normalizer->normalizeE164($fixture['raw']);
    $dirtyPem = "\r\n".implode("\r\n \t\r\n", array_map(
        static fn (string $line): string => " \t".$line." \t",
        explode("\n", $request['public_key']),
    ))."\r\n";
    $valid = $number === $fixture['normalized']
        && $number === $request['client_phone_number']
        && $rawNumber === $number
        && hash('sha256', $number) === $fixture['hash']
        && $verifier->normalizePublicKey($dirtyPem) === $request['public_key']
        && $verifier->reportPayload($number, $request['feature'], $dirtyPem) === $fixture['payload']
        && $verifier->verifyReport($number, $request['feature'], $dirtyPem, $request['signature'])
        && ! $verifier->verifyReport($number, 'non_payment', $dirtyPem, $request['signature'])
        && ! $verifier->verifyReport('+421900000099', $request['feature'], $dirtyPem, $request['signature'])
        && ! $verifier->verifyReport($number, $request['feature'], $dirtyPem, 'not-base64!');
    if (! $valid) {
        fwrite(STDERR, "Report normalization/canonicalization/signature interoperability failed.\n");
        exit(1);
    }
    if ($fixture['raw'] !== $number
        && $verifier->verifyReport($number, $request['feature'], $dirtyPem, $fixture['raw_signature'])) {
        fwrite(STDERR, "A raw-input signature unexpectedly passed normalized verification.\n");
        exit(1);
    }
    $reportCount++;
    $query = $fixture['query'];
    if ($verifier->blacklistCheckPayload($fixture['hash'], $dirtyPem) !== $fixture['query_payload']
        || ! $verifier->verifyBlacklistCheck($fixture['hash'], $dirtyPem, $query['signature'])
        || $verifier->verifyBlacklistCheck(str_repeat('0', 64), $dirtyPem, $query['signature'])) {
        fwrite(STDERR, "Query signature interoperability failed.\n");
        exit(1);
    }
    $queryCount++;
}

echo json_encode(['reports' => $reportCount, 'queries' => $queryCount], JSON_THROW_ON_ERROR)."\n";
