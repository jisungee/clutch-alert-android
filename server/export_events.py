"""Export installation-level data from the DB. Run on an authorized computer only."""
import argparse
import csv
from pathlib import Path
from sqlalchemy import select
from database import BehaviorEvent, make_database


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--out', required=True, type=Path)
    parser.add_argument('--installation-id')
    args = parser.parse_args()
    engine, factory = make_database()
    columns = [c for c in BehaviorEvent.__table__.columns if c.name not in {'payload_hash', 'dedupe_key'}]
    query = select(*columns).order_by(BehaviorEvent.received_at, BehaviorEvent.event_id)
    if args.installation_id:
        query = query.where(BehaviorEvent.installation_id == args.installation_id)
    count = 0
    try:
        with factory() as db, args.out.open('x', encoding='utf-8-sig', newline='') as output:
            writer = csv.writer(output)
            writer.writerow([c.name for c in columns])
            for row in db.execute(query.execution_options(yield_per=1000)):
                # Neutralize formula-like strings when opening untrusted event fields in Excel.
                writer.writerow(["'" + v if isinstance(v, str) and v.lstrip().startswith(('=', '+', '-', '@')) else v for v in row])
                count += 1
    finally:
        engine.dispose()
    print(f'Exported {count} rows to {args.out}')

if __name__ == '__main__':
    main()
