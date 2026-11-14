import unittest
from triage_osv import affected, in_events


class AdvisoryRangeTests(unittest.TestCase):
    def test_fixed_boundary_is_not_affected(self):
        events=[{'introduced':'16.0.0'},{'fixed':'16.4.1'}]
        self.assertTrue(in_events('16.3.8',events))
        self.assertFalse(in_events('16.4.1',events))

    def test_disjoint_reintroduced_ranges_do_not_hide_later_affected_branch(self):
        record={'affected':[{'package':{'name':'next','ecosystem':'npm'},'ranges':[
            {'type':'SEMVER','events':[{'introduced':'10.0.0'},{'fixed':'15.5.28'}]},
            {'type':'SEMVER','events':[{'introduced':'16.0.0'},{'fixed':'16.4.1'}]}]}]}
        self.assertTrue(affected(record,'npm','next','16.3.8'))
        self.assertFalse(affected(record,'npm','next','16.4.1'))

    def test_calver_last_affected_and_limit_boundaries(self):
        self.assertTrue(in_events('2026.7.22',[{'introduced':'2019.03.09'},{'fixed':'2026.10.18'}]))
        self.assertTrue(in_events('1.2.0',[{'introduced':'0'},{'last_affected':'1.2.0'}]))
        self.assertFalse(in_events('1.2.0',[{'introduced':'0'},{'limit':'1.2.0'}]))

    def test_unknown_git_range_cannot_manufacture_unaffected_result(self):
        with self.assertRaises(ValueError):
            affected({'affected':[{'package':{'name':'x','ecosystem':'npm'},'ranges':[
                {'type':'GIT','events':[{'introduced':'abcdef'}]}]}]},'npm','x','1.0.0')
