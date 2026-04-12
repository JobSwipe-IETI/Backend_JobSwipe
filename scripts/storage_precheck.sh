#!/usr/bin/env bash
set -euo pipefail

printf "Storage precheck for JobSwipe\n"
printf "============================\n"

provider="${APP_STORAGE_PROVIDER:-local}"
printf "Provider: %s\n" "$provider"

if [[ "$provider" == "local" ]]; then
  dir="${APP_UPLOADS_DIR:-uploads}"
  mkdir -p "$dir"
  printf "Local mode OK. Upload dir ready: %s\n" "$dir"
  exit 0
fi

if [[ "$provider" != "s3" ]]; then
  printf "ERROR: APP_STORAGE_PROVIDER must be 'local' or 's3'.\n" >&2
  exit 1
fi

required_vars=(APP_S3_BUCKET APP_S3_REGION APP_S3_PREFIX)
missing=0
for var in "${required_vars[@]}"; do
  if [[ -z "${!var:-}" ]]; then
    printf "ERROR: Missing env var %s\n" "$var" >&2
    missing=1
  fi
done

if [[ "$missing" -ne 0 ]]; then
  printf "\nSet the missing vars and run again.\n" >&2
  exit 1
fi

if [[ -z "${AWS_ACCESS_KEY_ID:-}" && -z "${AWS_PROFILE:-}" ]]; then
  printf "WARNING: No AWS credentials detected via AWS_ACCESS_KEY_ID or AWS_PROFILE.\n"
  printf "The app can still work if credentials are provided by IAM role.\n"
fi

printf "S3 mode basic config looks OK.\n"
printf "Bucket: %s\n" "$APP_S3_BUCKET"
printf "Region: %s\n" "$APP_S3_REGION"
printf "Prefix: %s\n" "$APP_S3_PREFIX"

printf "\nNext: start backend and execute vacancy multipart upload flow.\n"
