
from test_result import TestResult

RESULTS_FILE = 'RESULT.log'

class SequencerResult:
    """ Results of a single sequencer run  """

    def __init__(self, site_path, device_id):
        self.site_path = site_path
        self.device_id = device_id
        self.results_path = os.path.join(site_path, f'out/devices/{device_id}/sequencer_{device_id}.json')
        self.results = {}

        try:
            with open(self.results_path) as f:
                self.raw_results = json.load(f)
        except Exception as e:
            raise(e)

        for feature, sequences in self.raw_results['features'].items():
            for name, result in sequences['sequences'].items():
                self.results[name] = TestResult(
                    feature,
                    name,
                    result['result'],
                    result['stage'],
                    result['status']['message']
                )

    
    def __repr__(self):
        return(str(self.results))



results = SequencerResult('../../sites/udmi_site_model', 'AHU-1')
print(results.results)
#print(results.tests)



        