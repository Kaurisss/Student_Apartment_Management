"""Validate building groups and transactional room batches; remove only this run's test data."""
import uuid
from urllib.parse import quote
from smoke_test import Client


def main():
    admin, student, guest = Client(), Client(), Client()
    admin.login('admin')
    student.login('student01')
    prefix = '批量验收' + uuid.uuid4().hex[:8]
    names = [prefix + 'A', prefix + 'B']
    body = {'building': names[0], 'startRoom': '003', 'endRoom': '005', 'roomType': '4人间', 'createBeds': True}
    assert guest.call('/admin/dormitories/buildings')[0] == 401
    assert student.call('/admin/dormitories/buildings')[0] == 403
    assert student.call('/admin/dormitories/batch', 'POST', body)[0] == 403
    try:
        assert admin.ok('/admin/dormitories/batch', 'POST', body) == {'rooms': 3, 'beds': 12}
        rooms = admin.ok('/admin/dormitories/rooms?building=' + quote(names[0]))
        assert [row['room_no'] for row in rooms['rows']] == ['003', '004', '005']
        assert all(row['beds'] == 4 and row['occupied'] == 0 for row in rooms['rows'])
        admin.fail('/admin/dormitories/batch', 'POST', {**body, 'startRoom': '001', 'endRoom': '003'}, '本次未添加任何房间')
        assert admin.ok('/admin/dormitories/rooms?building=' + quote(names[0]))['total'] == 3
        admin.fail('/admin/dormitories/batch', 'POST', {**body, 'startRoom': '8', 'endRoom': '7'}, '房号范围无效')
        admin.fail('/admin/dormitories/batch', 'POST', {**body, 'startRoom': '1', 'endRoom': '201'}, '最多添加200间')
        admin.fail('/admin/dormitories/batch', 'POST', {**body, 'roomType': '8人间'}, '四人间或六人间')
        result = admin.ok('/admin/dormitories/batch', 'POST', {**body, 'building': names[1], 'startRoom': '101', 'endRoom': '111', 'roomType': '6人间', 'createBeds': False})
        assert result == {'rooms': 11, 'beds': 0}
        assert admin.ok('/admin/dormitories/rooms?building=' + quote(names[1]))['total'] == 11
        second = admin.ok('/admin/dormitories/rooms?building=' + quote(names[1]) + '&page=2')
        assert len(second['rows']) == 1 and second['rows'][0]['room_no'] == '111'
        groups = {row['building']: row for row in admin.ok('/admin/dormitories/buildings')}
        assert groups[names[0]]['rooms'] == 3 and groups[names[0]]['beds'] == 12
        assert groups[names[1]]['rooms'] == 11 and groups[names[1]]['beds'] == 0
        admin.ok('/admin/dormitories/batch', 'POST', {**body, 'building': names[1], 'startRoom': '201', 'endRoom': '201', 'roomType': '6人间'})
        assert next(row for row in admin.ok('/admin/dormitories/buildings') if row['building'] == names[1])['beds'] == 6
        print('PASS building isolation, pagination, zero-padded rooms, four/six-bed creation, no-bed option, batch rollback and permissions')
    finally:
        room_ids = []
        for name in names:
            data = admin.ok('/admin/dormitories/rooms?building=' + quote(name))
            for page in range(1, data['pages'] + 1):
                room_ids.extend(row['id'] for row in admin.ok('/admin/dormitories/rooms?building=' + quote(name) + '&page=' + str(page))['rows'])
        bed_page = admin.ok('/admin/beds')
        bed_ids = []
        for page in range(1, bed_page['data']['pages'] + 1):
            bed_ids.extend(row['id'] for row in admin.ok('/admin/beds?page=' + str(page))['data']['rows'] if row['dormitory_id'] in room_ids)
        for bed_id in bed_ids:
            admin.ok('/admin/beds/' + str(bed_id), 'DELETE')
        for room_id in room_ids:
            admin.ok('/admin/dormitories/' + str(room_id), 'DELETE')
        print('PASS removed only test-run rooms and beds')


if __name__ == '__main__':
    main()
