#!/bin/bash

docker compose exec -T postgres psql \
  -U activation \
  -d activation \
  -c "
    SELECT
        id,
        external_id,
        product_code,
        status,
        created_at
    FROM activation_requests
    ORDER BY created_at DESC;
  "